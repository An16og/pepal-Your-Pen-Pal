package local.pepal.journal.service;

import local.pepal.journal.ai.PersonaPromptBuilder;
import local.pepal.journal.ai.PersonaPromptBuilder.TaskType;
import local.pepal.journal.api.JournalDtos.ChatMessageDto;
import local.pepal.journal.api.JournalDtos.ChatResponse;
import local.pepal.journal.api.JournalDtos.EntryResponse;
import local.pepal.journal.api.JournalDtos.SettingsRecord;
import local.pepal.journal.repository.JournalRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JournalChatService {
    private final ChatClient chatClient;
    private final JournalService journalService;
    private final JournalRepository journalRepository;
    private final SettingsService settingsService;
    private final PersonaPromptBuilder promptBuilder;

    private static final Pattern DATE_PATTERN_DAY_MONTH = Pattern.compile(
            "(?i)\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)(?:\\s+(\\d{4}))?\\b");

    private static final Pattern DATE_PATTERN_MONTH_DAY = Pattern.compile(
            "(?i)\\b(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:\\s*,?\\s*(\\d{4}))?\\b");

    private static final Pattern DATE_PATTERN_ISO = Pattern.compile(
            "\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");

    public JournalChatService(
            ChatClient chatClient,
            JournalService journalService,
            JournalRepository journalRepository,
            SettingsService settingsService,
            PersonaPromptBuilder promptBuilder
    ) {
        this.chatClient = chatClient;
        this.journalService = journalService;
        this.journalRepository = journalRepository;
        this.settingsService = settingsService;
        this.promptBuilder = promptBuilder;
    }

    public ChatResponse ask(String question) {
        return ask(question, null);
    }

    public ChatResponse ask(String question, List<ChatMessageDto> history) {
        if (question == null || question.isBlank()) {
            return new ChatResponse("How can I reflect with you today?");
        }

        // 1. Extract any mentioned date
        Optional<LocalDate> targetDateOpt = extractDateFromQuestion(question);
        List<String> dateNotes = new ArrayList<>();
        List<EntryResponse> dateSpecificEntries = new ArrayList<>();

        if (targetDateOpt.isPresent()) {
            LocalDate targetDate = targetDateOpt.get();
            List<EntryResponse> entriesOnDate = journalRepository.findByDate(targetDate);
            if (entriesOnDate.isEmpty()) {
                java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy");
                return new ChatResponse("You didn't write any journal entry on " + targetDate.format(fmt) + ".");
            } else {
                dateSpecificEntries.addAll(entriesOnDate);
            }
        }

        // 2. Fetch recent entries (last 5) so the model always has the latest life context
        List<EntryResponse> recentEntries = journalRepository.findRecentBefore(LocalDate.now().plusDays(1), 5);

        // 3. Fetch semantic matches if query is meaningful (> 4 chars and not conversational glue)
        List<EntryResponse> semanticMatches = new ArrayList<>();
        String trimmed = question.strip().toLowerCase();
        boolean isShortGlue = trimmed.length() <= 10 && (
                trimmed.equals("yes") || trimmed.equals("no") || trimmed.equals("ok") ||
                trimmed.equals("okay") || trimmed.equals("ha") || trimmed.equals("haan") ||
                trimmed.equals("hlo") || trimmed.equals("hi") || trimmed.equals("hello") ||
                trimmed.equals("tell me more") || trimmed.equals("kya") || trimmed.equals("aur batao"));

        if (!isShortGlue) {
            try {
                semanticMatches = journalService.relevantEntries(question);
            } catch (Exception ignored) {
                // If semantic retrieval fails, fall back gracefully to recent entries
            }
        }

        // 3b. Fetch low mood and recharge entries if emotional query
        List<EntryResponse> emotionalMatches = new ArrayList<>();
        if (isEmotionalQuery(question)) {
            try {
                emotionalMatches.addAll(journalRepository.findRecentLowMoodOrEnergy(3));
                emotionalMatches.addAll(journalRepository.findRecentRechargeMoments(3));
            } catch (Exception ignored) {}
        }

        // 4. Combine entries with priority to semantic and date matches
        Map<Long, EntryResponse> combinedMap = new LinkedHashMap<>();
        for (EntryResponse e : dateSpecificEntries) combinedMap.put(e.id(), e);
        for (EntryResponse e : semanticMatches) combinedMap.put(e.id(), e);
        for (EntryResponse e : emotionalMatches) combinedMap.put(e.id(), e);
        for (EntryResponse e : recentEntries) combinedMap.put(e.id(), e);

        List<EntryResponse> allEntries = new ArrayList<>(combinedMap.values());

        if (allEntries.isEmpty() && dateNotes.isEmpty()) {
            return new ChatResponse("You haven't written any journal entries yet. Once you write your first reflection, I can help you spot patterns and reflect on your days.");
        }

        // 5. Construct compact, focused notes for qwen:4b (prevents context drowning)
        StringBuilder notesBuilder = new StringBuilder();
        if (!dateSpecificEntries.isEmpty()) {
            for (EntryResponse e : dateSpecificEntries) {
                notesBuilder.append(String.format("- %s (mood: %s, energy: %s): %s\n",
                        e.entryDate(), e.mood(), e.energy(), e.body().strip()));
            }
        } else if (isEmotionalQuery(question)) {
            // Include low energy / stress moments
            for (EntryResponse e : emotionalMatches) {
                if ("BAD".equalsIgnoreCase(e.mood()) || "LOW".equalsIgnoreCase(e.energy())) {
                    notesBuilder.append(String.format("- %s (drained/stress): %s\n",
                            e.entryDate(), summarizeShort(e.body(), 110)));
                } else {
                    notesBuilder.append(String.format("- %s (recharge/comfort): %s\n",
                            e.entryDate(), summarizeShort(e.body(), 110)));
                }
            }
            for (EntryResponse e : recentEntries.stream().limit(2).toList()) {
                notesBuilder.append(String.format("- %s: %s\n",
                        e.entryDate(), summarizeShort(e.body(), 110)));
            }
        } else {
            for (EntryResponse e : allEntries.stream().limit(6).toList()) {
                notesBuilder.append(String.format("- %s (%s, %s): %s\n",
                        e.entryDate(), e.mood(), e.energy(), summarizeShort(e.body(), 140)));
            }
        }

        // 6. Format recent conversation history if provided
        StringBuilder historyBuilder = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            historyBuilder.append("Recent Conversation Context:\n");
            for (ChatMessageDto msg : history) {
                if (msg.content() != null && !msg.content().isBlank()) {
                    String speaker = "user".equalsIgnoreCase(msg.role()) ? "User" : "pepal";
                    historyBuilder.append(speaker).append(": ").append(msg.content().strip()).append("\n");
                }
            }
            historyBuilder.append("\n");
        }

        // 7. Compose system and user prompt
        SettingsRecord settings = settingsService.getSettings();
        String baseSystemPrompt = promptBuilder.build(settings, TaskType.CHAT);
        String systemPrompt = (baseSystemPrompt == null || baseSystemPrompt.isBlank() ? "" : baseSystemPrompt + "\n\n") + """
                You are pepal, a personal journal companion.
                Always reply directly to the user as "you".
                Keep your reply warm, grounded in their notes, and concise (2-3 sentences max).
                Never preach, never list generic medical or legal causes, and avoid generic coping advice.
                Ground your reply strictly in the user's journal notes provided.
                If something is not in their journal notes, state clearly that you couldn't find any mention of it in their journal.
                """;

        String annotatedQuestion = annotateQuestionIntent(question.strip());

        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append("Journal Notes:\n");
        userPromptBuilder.append(notesBuilder.toString().strip()).append("\n\n");

        if (historyBuilder.length() > 0) {
            userPromptBuilder.append(historyBuilder.toString().strip()).append("\n\n");
        }

        if (!dateSpecificEntries.isEmpty()) {
            userPromptBuilder.append("Instruction: Reply directly to the user addressing them as 'you' (e.g. 'On that date, you noted...'). Summarize their reflection on that date.");
        } else if (isEmotionalQuery(question)) {
            userPromptBuilder.append("Question: ").append(annotatedQuestion)
                    .append(". Looking at my journal notes above, what has been draining my energy recently, and what helped me recharge before?");
        } else {
            userPromptBuilder.append("Question: ").append(annotatedQuestion)
                    .append("\nInstruction: Answer directly and concisely based strictly on the journal notes above.");
        }

        String answer = chatClient.prompt()
                .system(systemPrompt)
                .user(userPromptBuilder.toString().strip())
                .options(org.springframework.ai.ollama.api.OllamaOptions.builder()
                        .temperature(0.2)
                        .repeatPenalty(1.2)
                        .numPredict(180)
                        .build())
                .call()
                .content();

        String cleaned = cleanResponse(answer);
        return new ChatResponse(cleaned.isBlank()
                ? "The journal does not contain enough information to answer that." : cleaned);
    }

    private String cleanResponse(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String[] lines = raw.split("\r?\n");
        StringBuilder sb = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                sb.append("\n");
                continue;
            }
            if (trimmed.startsWith("Question:") || trimmed.startsWith("User message:") ||
                    trimmed.startsWith("User says:") || trimmed.startsWith("Instructions:") ||
                    trimmed.startsWith("Instruction:")) {
                continue;
            }
            if (seen.contains(trimmed)) {
                break; // Stop repetition loop
            }
            seen.add(trimmed);
            sb.append(line).append("\n");
        }
        String result = sb.toString().strip();
        if (!result.endsWith(".") && !result.endsWith("!") && !result.endsWith("?")) {
            int lastPeriod = Math.max(result.lastIndexOf('.'), Math.max(result.lastIndexOf('!'), result.lastIndexOf('?')));
            if (lastPeriod > 0) {
                result = result.substring(0, lastPeriod + 1);
            }
        }
        return result.strip();
    }

    private boolean isEmotionalQuery(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase();
        return lower.contains("sad") || lower.contains("down") || lower.contains("low") ||
               lower.contains("tired") || lower.contains("exhaust") || lower.contains("overwhelm") ||
               lower.contains("theek nahi") || lower.contains("thik nahi") || lower.contains("khush nahi") ||
               lower.contains("anxious") || lower.contains("anxiety") || lower.contains("burnout") ||
               lower.contains("heavy") || lower.contains("depress") || lower.contains("slump") ||
               lower.contains("bura") || lower.contains("udaas") || lower.contains("stress") ||
               lower.contains("struggl") || lower.contains("cope") || lower.contains("lonely") || lower.contains("alone");
    }

    private String annotateQuestionIntent(String question) {
        if (question == null) return "";
        String lower = question.toLowerCase();
        if (lower.contains("theek nahi") || lower.contains("thik nahi") || lower.contains("accha nahi")) {
            return question + " (I have been feeling low or down lately)";
        }
        if (lower.contains("man nahi lag") || lower.contains("mann nahi lag")) {
            return question + " (feeling disconnected or unmotivated)";
        }
        if (lower.contains("bura lag") || lower.contains("udaas")) {
            return question + " (feeling sad or down)";
        }
        return question;
    }

    private Optional<LocalDate> extractDateFromQuestion(String text) {
        if (text == null) return Optional.empty();

        // 1. ISO format: 2026-09-27
        Matcher isoMatcher = DATE_PATTERN_ISO.matcher(text);
        if (isoMatcher.find()) {
            try {
                int year = Integer.parseInt(isoMatcher.group(1));
                int month = Integer.parseInt(isoMatcher.group(2));
                int day = Integer.parseInt(isoMatcher.group(3));
                return Optional.of(LocalDate.of(year, month, day));
            } catch (Exception ignored) {}
        }

        // 2. Day Month: 27 September, 27th sept 2026
        Matcher dmMatcher = DATE_PATTERN_DAY_MONTH.matcher(text);
        if (dmMatcher.find()) {
            try {
                int day = Integer.parseInt(dmMatcher.group(1));
                int month = parseMonth(dmMatcher.group(2));
                int year = dmMatcher.group(3) != null ? Integer.parseInt(dmMatcher.group(3)) : LocalDate.now().getYear();
                return Optional.of(LocalDate.of(year, month, day));
            } catch (Exception ignored) {}
        }

        // 3. Month Day: September 27, Sept 27th
        Matcher mdMatcher = DATE_PATTERN_MONTH_DAY.matcher(text);
        if (mdMatcher.find()) {
            try {
                int month = parseMonth(mdMatcher.group(1));
                int day = Integer.parseInt(mdMatcher.group(2));
                int year = mdMatcher.group(3) != null ? Integer.parseInt(mdMatcher.group(3)) : LocalDate.now().getYear();
                return Optional.of(LocalDate.of(year, month, day));
            } catch (Exception ignored) {}
        }

        return Optional.empty();
    }

    private static int parseMonth(String monthStr) {
        String m = monthStr.toLowerCase();
        if (m.startsWith("jan")) return 1;
        if (m.startsWith("feb")) return 2;
        if (m.startsWith("mar")) return 3;
        if (m.startsWith("apr")) return 4;
        if (m.startsWith("may")) return 5;
        if (m.startsWith("jun")) return 6;
        if (m.startsWith("jul")) return 7;
        if (m.startsWith("aug")) return 8;
        if (m.startsWith("sep")) return 9;
        if (m.startsWith("oct")) return 10;
        if (m.startsWith("nov")) return 11;
        if (m.startsWith("dec")) return 12;
        return 1;
    }

    private static String summarizeShort(String text, int maxLen) {
        if (text == null) return "";
        String clean = text.replaceAll("\\s+", " ").strip();
        if (clean.length() <= maxLen) return clean;
        return clean.substring(0, maxLen).strip() + "...";
    }
}
