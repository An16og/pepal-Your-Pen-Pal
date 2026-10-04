package local.pepal.journal.service;

import local.pepal.journal.ai.PersonaPromptBuilder;
import local.pepal.journal.ai.PersonaPromptBuilder.TaskType;
import local.pepal.journal.api.JournalDtos.EntryResponse;
import local.pepal.journal.api.JournalDtos.SettingsRecord;
import local.pepal.journal.api.JournalDtos.SummaryResponse;
import local.pepal.journal.api.JournalDtos.SummaryStats;
import local.pepal.journal.repository.JournalRepository;
import local.pepal.journal.repository.SummaryRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SummaryService {
    private final SummaryRepository summaryRepository;
    private final JournalRepository journalRepository;
    private final SettingsService settingsService;
    private final PersonaPromptBuilder promptBuilder;
    private final ChatClient chatClient;

    public SummaryService(
            SummaryRepository summaryRepository,
            JournalRepository journalRepository,
            SettingsService settingsService,
            PersonaPromptBuilder promptBuilder,
            ChatClient chatClient
    ) {
        this.summaryRepository = summaryRepository;
        this.journalRepository = journalRepository;
        this.settingsService = settingsService;
        this.promptBuilder = promptBuilder;
        this.chatClient = chatClient;
    }

    public List<SummaryResponse> getSummaries(String type, int limit, int offset) {
        String periodType = normalizeType(type);
        return summaryRepository.findByType(periodType, Math.max(1, limit), Math.max(0, offset));
    }

    public SummaryResponse getSummaryById(long id) {
        return summaryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Summary not found: " + id));
    }

    public List<EntryResponse> getSummaryEntries(long id) {
        SummaryResponse summary = getSummaryById(id);
        return journalRepository.findInDateRange(summary.periodStart(), summary.periodEnd().plusDays(1));
    }

    public SummaryResponse generateSummary(String type, LocalDate date, boolean force) {
        String periodType = normalizeType(type);
        LocalDate targetDate = date != null ? date : LocalDate.now();

        LocalDate periodStart;
        LocalDate periodEnd;

        switch (periodType) {
            case "WEEK" -> {
                periodStart = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                periodEnd = periodStart.plusDays(6);
            }
            case "MONTH" -> {
                periodStart = targetDate.withDayOfMonth(1);
                periodEnd = targetDate.with(TemporalAdjusters.lastDayOfMonth());
            }
            case "YEAR" -> {
                periodStart = targetDate.withDayOfYear(1);
                periodEnd = targetDate.with(TemporalAdjusters.lastDayOfYear());
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid summary period type: " + type);
        }

        if (periodStart.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot generate a summary for a future period.");
        }

        if (!force) {
            Optional<SummaryResponse> existing = summaryRepository.findByTypeAndStart(periodType, periodStart);
            if (existing.isPresent() && !existing.get().stale()) {
                return existing.get();
            }
        }

        List<EntryResponse> entries = journalRepository.findInDateRange(periodStart, periodEnd.plusDays(1));
        if (entries.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No entries found in that period to summarize.");
        }

        // Calculate statistics
        SummaryStats stats = calculateStats(entries, periodStart, periodEnd);
        boolean isFinal = periodEnd.isBefore(LocalDate.now());

        // Generate AI synthesis or fallback to stats-only
        String headline = String.format("%s Reflection (%s)",
                capitalize(periodType.toLowerCase()),
                formatPeriodLabel(periodType, periodStart, periodEnd));
        String body;
        List<String> themes = new ArrayList<>();
        String source = "AI";

        try {
            GeneratedAiContent aiContent = generateAiReflection(periodType, periodStart, periodEnd, entries, stats);
            if (aiContent != null && !aiContent.body().isBlank()) {
                if (aiContent.headline() != null && !aiContent.headline().isBlank()) {
                    headline = aiContent.headline();
                }
                body = aiContent.body();
                if (aiContent.themes() != null && !aiContent.themes().isEmpty()) {
                    themes = aiContent.themes();
                }
            } else {
                body = buildStatsFallbackBody(periodType, stats);
                source = "STATS_ONLY";
                themes = List.of("Reflection", "Patterns");
            }
        } catch (Exception e) {
            body = buildStatsFallbackBody(periodType, stats);
            source = "STATS_ONLY";
            themes = List.of("Reflection", "Patterns");
        }

        return summaryRepository.upsert(
                periodType,
                periodStart,
                periodEnd,
                headline,
                body,
                themes,
                stats,
                source,
                isFinal,
                false
        );
    }

    private SummaryStats calculateStats(List<EntryResponse> entries, LocalDate start, LocalDate end) {
        Set<LocalDate> activeDates = entries.stream().map(EntryResponse::entryDate).collect(Collectors.toSet());
        int daysJournaled = activeDates.size();
        int daysInPeriod = (int) ChronoUnit.DAYS.between(start, end) + 1;
        int totalEntries = entries.size();

        Set<LocalDate> goodMoodDates = entries.stream()
                .filter(e -> "GOOD".equalsIgnoreCase(e.mood()))
                .map(EntryResponse::entryDate)
                .collect(Collectors.toSet());

        Set<LocalDate> highEnergyDates = entries.stream()
                .filter(e -> "HIGH".equalsIgnoreCase(e.energy()))
                .map(EntryResponse::entryDate)
                .collect(Collectors.toSet());

        int totalWords = entries.stream()
                .mapToInt(e -> e.body() == null ? 0 : e.body().strip().split("\\s+").length)
                .sum();

        int longestStreak = calculateStreak(activeDates);

        return new SummaryStats(
                daysJournaled,
                daysInPeriod,
                totalEntries,
                longestStreak,
                goodMoodDates.size(),
                highEnergyDates.size(),
                totalWords
        );
    }

    private int calculateStreak(Set<LocalDate> dates) {
        if (dates.isEmpty()) return 0;
        List<LocalDate> sorted = new ArrayList<>(dates);
        Collections.sort(sorted);

        int maxStreak = 1;
        int currentStreak = 1;

        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).minusDays(1).equals(sorted.get(i - 1))) {
                currentStreak++;
                maxStreak = Math.max(maxStreak, currentStreak);
            } else {
                currentStreak = 1;
            }
        }
        return maxStreak;
    }

    private record GeneratedAiContent(String headline, String body, List<String> themes) {}

    private GeneratedAiContent generateAiReflection(
            String periodType,
            LocalDate start,
            LocalDate end,
            List<EntryResponse> entries,
            SummaryStats stats
    ) {
        SettingsRecord settings = settingsService.getSettings();
        String baseSystemPrompt = promptBuilder.build(settings, TaskType.SUMMARY);

        String systemPrompt = (baseSystemPrompt == null || baseSystemPrompt.isBlank() ? "" : baseSystemPrompt + "\n\n") + """
                You are pepal, a reflective personal journal companion.
                Your task is to review the user's journal entries for a given period and write an inspiring, grounded summary.
                Provide:
                1. Headline: A brief, evocative phrase (max 8 words).
                2. Themes: 2 to 4 key themes (e.g. #DeepWork, #Rest, #Connection).
                3. Narrative: A warm 2-3 paragraph reflection synthesizing what energized them, what drained them, and what growth occurred. Address them directly as "you".
                
                Format your reply cleanly:
                HEADLINE: <headline>
                THEMES: <theme1>, <theme2>, <theme3>
                <narrative text>
                """;

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append(String.format("Period: %s (%s to %s)\n", periodType, start, end));
        userPrompt.append(String.format("Stats: Journaled %d of %d days | %d total entries | %d good mood days | %d high energy days | %d words\n\n",
                stats.daysJournaled(), stats.daysInPeriod(), stats.totalEntries(), stats.goodMoodDays(), stats.highEnergyDays(), stats.totalWords()));

        userPrompt.append("Journal Entries for this period:\n");
        // Limit to 10 entries for context safety on small models
        List<EntryResponse> sample = entries.stream().limit(10).toList();
        for (EntryResponse e : sample) {
            String preview = e.body().length() > 140 ? e.body().substring(0, 140) + "…" : e.body();
            userPrompt.append(String.format("- %s (%s, %s): %s\n", e.entryDate(), e.mood(), e.energy(), preview.strip()));
        }

        userPrompt.append("\nWrite the reflective summary now.");

        String rawResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt.toString().strip())
                .options(org.springframework.ai.ollama.api.OllamaOptions.builder()
                        .temperature(0.3)
                        .repeatPenalty(1.15)
                        .numPredict(350)
                        .build())
                .call()
                .content();

        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }

        return parseAiContent(rawResponse.strip());
    }

    private GeneratedAiContent parseAiContent(String text) {
        String headline = null;
        List<String> themes = new ArrayList<>();
        StringBuilder bodyBuilder = new StringBuilder();

        String[] lines = text.split("\r?\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.toUpperCase().startsWith("HEADLINE:")) {
                headline = trimmed.substring("HEADLINE:".length()).trim();
            } else if (trimmed.toUpperCase().startsWith("THEMES:")) {
                String themesPart = trimmed.substring("THEMES:".length()).trim();
                for (String t : themesPart.split("[,#;]")) {
                    String cleanTheme = t.trim().replaceAll("^#+", "");
                    if (!cleanTheme.isBlank()) {
                        themes.add(cleanTheme);
                    }
                }
            } else {
                bodyBuilder.append(line).append("\n");
            }
        }

        String body = bodyBuilder.toString().strip();
        if (themes.isEmpty()) {
            themes.addAll(List.of("Reflection", "Rhythms"));
        }
        return new GeneratedAiContent(headline, body, themes);
    }

    private String buildStatsFallbackBody(String periodType, SummaryStats stats) {
        return String.format(
                "During this %s, you reflected on %d out of %d days with %d total entries (%d words). " +
                "You recorded %d good mood days and %d high energy days, maintaining a peak streak of %d consecutive days of journaling.",
                periodType.toLowerCase(),
                stats.daysJournaled(),
                stats.daysInPeriod(),
                stats.totalEntries(),
                stats.totalWords(),
                stats.goodMoodDays(),
                stats.highEnergyDays(),
                stats.longestStreak()
        );
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) return "WEEK";
        String upper = type.trim().toUpperCase();
        if (upper.startsWith("YEAR")) return "YEAR";
        if (upper.startsWith("MONTH")) return "MONTH";
        return "WEEK";
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return "";
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private String formatPeriodLabel(String type, LocalDate start, LocalDate end) {
        DateTimeFormatter mediumFmt = DateTimeFormatter.ofPattern("d MMM yyyy");
        if ("YEAR".equals(type)) {
            return String.valueOf(start.getYear());
        } else if ("MONTH".equals(type)) {
            return start.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        } else {
            return start.format(mediumFmt) + " – " + end.format(mediumFmt);
        }
    }
}
