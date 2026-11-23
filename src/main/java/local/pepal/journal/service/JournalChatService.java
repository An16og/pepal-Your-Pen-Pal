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

    /** Cosine distance cutoff for semantic neighbors (0 identical, ~1 orthogonal). */
    private static final double SEMANTIC_MAX_DISTANCE = 0.55;
    private static final int SEMANTIC_LIMIT = 6;
    private static final int NOTE_BODY_CHARS = 480;
    private static final int MAX_NOTES = 8;

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

        Optional<LocalDate> targetDateOpt = extractDateFromQuestion(question);
        List<EntryResponse> dateSpecificEntries = new ArrayList<>();

        if (targetDateOpt.isPresent()) {
            LocalDate targetDate = targetDateOpt.get();
            List<EntryResponse> entriesOnDate = journalRepository.findByDate(targetDate);
            if (entriesOnDate.isEmpty()) {
                java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy");
                return new ChatResponse("You didn't write any journal entry on " + targetDate.format(fmt) + ".");
            }
            dateSpecificEntries.addAll(entriesOnDate);
        }

        List<EntryResponse> semanticMatches = new ArrayList<>();
        String trimmed = question.strip().toLowerCase();
        boolean isShortGlue = trimmed.length() <= 10 && (
                trimmed.equals("yes") || trimmed.equals("no") || trimmed.equals("ok") ||
                trimmed.equals("okay") || trimmed.equals("ha") || trimmed.equals("haan") ||
                trimmed.equals("hlo") || trimmed.equals("hi") || trimmed.equals("hello") ||
                trimmed.equals("tell me more") || trimmed.equals("kya") || trimmed.equals("aur batao"));

        if (!isShortGlue && dateSpecificEntries.isEmpty()) {
            try {
                semanticMatches = journalService.relevantEntries(question, SEMANTIC_LIMIT, SEMANTIC_MAX_DISTANCE);
            } catch (Exception ignored) {
                // Fall back to recency / emotional heuristics below
            }
        }

        List<EntryResponse> emotionalMatches = new ArrayList<>();
        EmotionalFocus emotionalFocus = classifyEmotionalFocus(question);
        if (emotionalFocus != EmotionalFocus.NONE) {
            try {
                if (emotionalFocus == EmotionalFocus.DRAIN || emotionalFocus == EmotionalFocus.BOTH) {
                    emotionalMatches.addAll(journalRepository.findRecentLowMoodOrEnergy(4));
                }
                if (emotionalFocus == EmotionalFocus.RECHARGE || emotionalFocus == EmotionalFocus.BOTH) {
                    emotionalMatches.addAll(journalRepository.findRecentRechargeMoments(4));
                }
            } catch (Exception ignored) {}
        }

        // Recency is a backfill only — prefer semantic / date / emotional hits so
        // the model is not drowned in unrelated recent notes.
        List<EntryResponse> recentEntries = journalRepository.findRecentBefore(LocalDate.now().plusDays(1), 3);

        Map<Long, EntryResponse> combinedMap = new LinkedHashMap<>();
        for (EntryResponse e : dateSpecificEntries) combinedMap.put(e.id(), e);
        for (EntryResponse e : semanticMatches) combinedMap.put(e.id(), e);
        for (EntryResponse e : emotionalMatches) combinedMap.put(e.id(), e);

        boolean weakRetrieval = dateSpecificEntries.isEmpty()
                && semanticMatches.isEmpty()
                && emotionalMatches.size() < 2;
        if (weakRetrieval || isShortGlue) {
            for (EntryResponse e : recentEntries) combinedMap.put(e.id(), e);
        } else if (combinedMap.size() < 3) {
            for (EntryResponse e : recentEntries) {
                combinedMap.putIfAbsent(e.id(), e);
                if (combinedMap.size() >= 4) break;
            }
        }

        List<EntryResponse> allEntries = new ArrayList<>(combinedMap.values());

        if (allEntries.isEmpty()) {
            return new ChatResponse("You haven't written any journal entries yet. Once you write your first reflection, I can help you spot patterns and reflect on your days.");
        }

        String notes = buildNotesBlock(dateSpecificEntries, emotionalMatches, allEntries, question);
        String historyBlock = formatHistory(history);

        SettingsRecord settings = settingsService.getSettings();
        String baseSystemPrompt = promptBuilder.build(settings, TaskType.CHAT);
        String systemPrompt = (baseSystemPrompt == null || baseSystemPrompt.isBlank() ? "" : baseSystemPrompt + "\n\n") + """
                You are pepal, a personal journal companion answering from Journal Notes below.
                Rules:
                - Speak to the user as "you".
                - Every claim must be grounded in a dated note. Cite the date when you mention a fact.
                - Use concrete nouns from the notes (people, places, food, activities, sleep, work).
                - Prefer 3-6 short sentences. Longer is fine only when the notes support it.
                - If the notes do not contain the answer, say you could not find it in their journal.
                - Never invent causes, advice, medical explanations, or coping tips that are not in the notes.
                """;

        String annotatedQuestion = annotateQuestionIntent(question.strip());
        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append("Journal Notes:\n").append(notes.strip()).append("\n\n");
        if (!historyBlock.isBlank()) {
            userPromptBuilder.append(historyBlock.strip()).append("\n\n");
        }

        if (!dateSpecificEntries.isEmpty()) {
            userPromptBuilder.append("Question: ").append(annotatedQuestion)
                    .append("\nInstruction: Summarize what they wrote on that date using their own details. Address them as 'you'.");
        } else if (emotionalFocus == EmotionalFocus.DRAIN) {
            userPromptBuilder.append("Question: ").append(annotatedQuestion)
                    .append("\nInstruction: From the notes, name what drained their energy. Cite dates and concrete causes. No generic advice.");
        } else if (emotionalFocus == EmotionalFocus.RECHARGE) {
            userPromptBuilder.append("Question: ").append(annotatedQuestion)
                    .append("\nInstruction: From the notes, name what helped them recharge or feel better. Cite dates and concrete activities. No generic advice.");
        } else if (emotionalFocus == EmotionalFocus.BOTH) {
            userPromptBuilder.append("Question: ").append(annotatedQuestion)
                    .append("\nInstruction: From the notes, name what drained them and what helped them recharge. Cite dates. No generic advice.");
        } else {
            userPromptBuilder.append("Question: ").append(annotatedQuestion)
                    .append("\nInstruction: Answer directly from the journal notes. Cite dates and specific details. If missing, say so.");
        }

        String answer = chatClient.prompt()
                .system(systemPrompt)
                .user(userPromptBuilder.toString().strip())
                .options(org.springframework.ai.ollama.api.OllamaOptions.builder()
                        .temperature(0.15)
                        .topP(0.9)
                        .repeatPenalty(1.15)
                        .numPredict(360)
                        .build())
                .call()
                .content();

        String cleaned = cleanResponse(answer);
        return new ChatResponse(cleaned.isBlank()
                ? "The journal does not contain enough information to answer that." : cleaned);
    }

    private String buildNotesBlock(
            List<EntryResponse> dateSpecificEntries,
            List<EntryResponse> emotionalMatches,
            List<EntryResponse> allEntries,
            String question
    ) {
        StringBuilder notesBuilder = new StringBuilder();
        if (!dateSpecificEntries.isEmpty()) {
            for (EntryResponse e : dateSpecificEntries) {
                notesBuilder.append(formatNote(e, Integer.MAX_VALUE));
            }
            return notesBuilder.toString();
        }

        Set<Long> written = new LinkedHashSet<>();
        EmotionalFocus focus = classifyEmotionalFocus(question);
        if (focus != EmotionalFocus.NONE) {
            for (EntryResponse e : emotionalMatches) {
                if (!written.add(e.id())) continue;
                boolean drained = "BAD".equalsIgnoreCase(e.mood()) || "LOW".equalsIgnoreCase(e.energy());
                String tag = drained ? "drained/stress" : "recharge/comfort";
                notesBuilder.append(String.format("- %s [%s] (mood: %s, energy: %s): %s\n",
                        e.entryDate(), tag, e.mood(), e.energy(), truncateBody(e.body(), NOTE_BODY_CHARS)));
            }
        }

        for (EntryResponse e : allEntries) {
            if (written.size() >= MAX_NOTES) break;
            if (!written.add(e.id())) continue;
            notesBuilder.append(formatNote(e, NOTE_BODY_CHARS));
        }
        return notesBuilder.toString();
    }

    private static String formatNote(EntryResponse e, int bodyLimit) {
        return String.format("- %s (mood: %s, energy: %s): %s\n",
                e.entryDate(), e.mood(), e.energy(), truncateBody(e.body(), bodyLimit));
    }

    private static String formatHistory(List<ChatMessageDto> history) {
        if (history == null || history.isEmpty()) return "";
        StringBuilder historyBuilder = new StringBuilder("Recent Conversation Context:\n");
        int start = Math.max(0, history.size() - 6);
        for (int i = start; i < history.size(); i++) {
            ChatMessageDto msg = history.get(i);
            if (msg.content() == null || msg.content().isBlank()) continue;
            String speaker = "user".equalsIgnoreCase(msg.role()) ? "User" : "pepal";
            historyBuilder.append(speaker).append(": ").append(msg.content().strip()).append("\n");
        }
        return historyBuilder.toString();
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
                    trimmed.startsWith("Instruction:") || trimmed.startsWith("Journal Notes:")) {
                continue;
            }
            if (seen.contains(trimmed)) {
                break;
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

    private enum EmotionalFocus { NONE, DRAIN, RECHARGE, BOTH }

    private EmotionalFocus classifyEmotionalFocus(String text) {
        if (text == null || text.isBlank()) return EmotionalFocus.NONE;
        String lower = text.toLowerCase();

        boolean drain = lower.contains("sad") || lower.contains("down") || lower.contains("tired") ||
                lower.contains("exhaust") || lower.contains("overwhelm") || lower.contains("theek nahi") ||
                lower.contains("thik nahi") || lower.contains("khush nahi") || lower.contains("anxious") ||
                lower.contains("anxiety") || lower.contains("burnout") || lower.contains("heavy") ||
                lower.contains("depress") || lower.contains("slump") || lower.contains("bura") ||
                lower.contains("udaas") || lower.contains("stress") || lower.contains("struggl") ||
                lower.contains("cope") || lower.contains("lonely") || lower.contains("alone") ||
                lower.contains("drain") || lower.contains("drained");

        boolean recharge = lower.contains("recharge") || lower.contains("helped") ||
                lower.contains("comfort") || lower.contains("better") || lower.contains("joy") ||
                lower.contains("grounded") || lower.contains("what helped") || lower.contains("energised") ||
                lower.contains("energized") || lower.contains("restored");

        boolean energyAsk = lower.contains("energy") || lower.contains("feeling") || lower.contains("feel");

        // "low energy" / "feeling low" are drain-leaning; bare "energy" with recharge words stays recharge
        if (lower.contains("low energy") || lower.contains("feeling low") || lower.matches(".*\\blow\\b.*")) {
            drain = true;
        }

        if (drain && recharge) return EmotionalFocus.BOTH;
        if (drain) return EmotionalFocus.DRAIN;
        if (recharge) return EmotionalFocus.RECHARGE;
        if (energyAsk) return EmotionalFocus.BOTH;
        return EmotionalFocus.NONE;
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

        Matcher isoMatcher = DATE_PATTERN_ISO.matcher(text);
        if (isoMatcher.find()) {
            try {
                int year = Integer.parseInt(isoMatcher.group(1));
                int month = Integer.parseInt(isoMatcher.group(2));
                int day = Integer.parseInt(isoMatcher.group(3));
                return Optional.of(LocalDate.of(year, month, day));
            } catch (Exception ignored) {}
        }

        Matcher dmMatcher = DATE_PATTERN_DAY_MONTH.matcher(text);
        if (dmMatcher.find()) {
            try {
                int day = Integer.parseInt(dmMatcher.group(1));
                int month = parseMonth(dmMatcher.group(2));
                int year = dmMatcher.group(3) != null ? Integer.parseInt(dmMatcher.group(3)) : LocalDate.now().getYear();
                return Optional.of(LocalDate.of(year, month, day));
            } catch (Exception ignored) {}
        }

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

    private static String truncateBody(String text, int maxLen) {
        if (text == null) return "";
        String clean = text.replaceAll("\\s+", " ").strip();
        if (maxLen == Integer.MAX_VALUE || clean.length() <= maxLen) return clean;
        return clean.substring(0, maxLen).strip() + "...";
    }
}
