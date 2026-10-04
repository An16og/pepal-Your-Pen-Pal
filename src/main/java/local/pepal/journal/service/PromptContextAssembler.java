package local.pepal.journal.service;

import local.pepal.journal.api.JournalDtos.EntryResponse;
import local.pepal.journal.api.JournalDtos.PromptQuestionDto;
import local.pepal.journal.repository.DailyPromptRepository;
import local.pepal.journal.repository.JournalRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Month;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class PromptContextAssembler {

    public record PromptContext(
            String renderedContext,
            int lowStreak,
            int nonDeletedCount,
            List<PromptQuestionDto> avoidQuestions
    ) {}

    private final JournalRepository journalRepository;
    private final DailyPromptRepository dailyPromptRepository;
    private final EmbeddingModel embeddingModel;
    private final LifeSnapshotProvider lifeSnapshotProvider;
    private final int maxContextChars;

    public PromptContextAssembler(
            JournalRepository journalRepository,
            DailyPromptRepository dailyPromptRepository,
            EmbeddingModel embeddingModel,
            LifeSnapshotProvider lifeSnapshotProvider,
            @Value("${otto.prompts.max-context-chars:${usher.prompts.max-context-chars:6000}}") int maxContextChars
    ) {
        this.journalRepository = journalRepository;
        this.dailyPromptRepository = dailyPromptRepository;
        this.embeddingModel = embeddingModel;
        this.lifeSnapshotProvider = lifeSnapshotProvider;
        this.maxContextChars = maxContextChars;
    }

    public PromptContext assemble(LocalDate date, List<PromptQuestionDto> avoidAdditional) {
        int nonDeletedCount = journalRepository.countNonDeletedBefore(date);

        // 1. Trend facts (last 14 days before D)
        LocalDate fourteenDaysAgo = date.minusDays(14);
        List<EntryResponse> entriesLast14Days = journalRepository.findInDateRange(fourteenDaysAgo, date);

        int goodMoodCount = 0;
        int badMoodCount = 0;
        int highEnergyCount = 0;
        int lowEnergyCount = 0;

        for (EntryResponse entry : entriesLast14Days) {
            if ("GOOD".equalsIgnoreCase(entry.mood())) goodMoodCount++;
            else if ("BAD".equalsIgnoreCase(entry.mood())) badMoodCount++;

            if ("HIGH".equalsIgnoreCase(entry.energy())) highEnergyCount++;
            else if ("LOW".equalsIgnoreCase(entry.energy())) lowEnergyCount++;
        }

        // Days journaled & low streak
        Map<LocalDate, List<EntryResponse>> byDate = entriesLast14Days.stream()
                .collect(Collectors.groupingBy(EntryResponse::entryDate));
        int daysJournaled = byDate.size();

        int lowStreak = calculateLowStreak(byDate);

        // 2. Calendar facts
        String dayOfWeek = date.getDayOfWeek().name();
        String season = deriveSeason(date);

        // 3. Recently asked questions (last 14 days before D) + optional avoidAdditional
        List<PromptQuestionDto> avoidQuestions = new ArrayList<>(dailyPromptRepository.findRecentQuestions(date, 14));
        if (avoidAdditional != null) {
            avoidQuestions.addAll(avoidAdditional);
        }

        // 4. Life snapshot
        Optional<String> lifeSnapshot = lifeSnapshotProvider.latestSnapshot(date);

        // 5. Recent entries (up to 5, truncated to ~400 chars)
        List<EntryResponse> recentEntries = journalRepository.findRecentBefore(date, 5);

        // 6. Relevant older entries (up to 3 via pgvector similarity)
        List<EntryResponse> similarEntries = List.of();
        if (!recentEntries.isEmpty()) {
            String combinedRecentText = recentEntries.stream()
                    .map(e -> truncate(e.body(), 400))
                    .collect(Collectors.joining("\n"));
            try {
                float[] embedding = embeddingModel.embed(combinedRecentText);
                List<Long> recentIds = recentEntries.stream().map(EntryResponse::id).toList();
                similarEntries = journalRepository.findSimilarBefore(embedding, date, recentIds, 3);
            } catch (Exception e) {
                // If embedding fails for similarity lookup, gracefully proceed without older similar entries
                similarEntries = List.of();
            }
        }

        // 7. Render context with budget trimming (< 6000 chars)
        String rendered = renderWithBudget(date, dayOfWeek, season, daysJournaled, goodMoodCount, badMoodCount,
                highEnergyCount, lowEnergyCount, lowStreak, lifeSnapshot, avoidQuestions,
                recentEntries, similarEntries, 400, true);

        return new PromptContext(rendered, lowStreak, nonDeletedCount, avoidQuestions);
    }

    private int calculateLowStreak(Map<LocalDate, List<EntryResponse>> byDate) {
        if (byDate.isEmpty()) {
            return 0;
        }
        List<LocalDate> sortedDates = byDate.keySet().stream()
                .sorted(Comparator.reverseOrder())
                .toList();

        int streak = 0;
        for (LocalDate d : sortedDates) {
            List<EntryResponse> dayEntries = byDate.get(d);
            boolean isLowDay = dayEntries.stream().anyMatch(e ->
                    "LOW".equalsIgnoreCase(e.energy()) || "BAD".equalsIgnoreCase(e.mood()));
            if (isLowDay) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    private String deriveSeason(LocalDate date) {
        Month m = date.getMonth();
        return switch (m) {
            case MARCH, APRIL, MAY -> "Spring (Northern)";
            case JUNE, JULY, AUGUST -> "Summer (Northern)";
            case SEPTEMBER, OCTOBER, NOVEMBER -> "Autumn/Fall (Northern)";
            case DECEMBER, JANUARY, FEBRUARY -> "Winter (Northern)";
        };
    }

    private String renderWithBudget(
            LocalDate date,
            String dayOfWeek,
            String season,
            int daysJournaled,
            int goodMoodCount,
            int badMoodCount,
            int highEnergyCount,
            int lowEnergyCount,
            int lowStreak,
            Optional<String> lifeSnapshot,
            List<PromptQuestionDto> avoidQuestions,
            List<EntryResponse> recentEntries,
            List<EntryResponse> similarEntries,
            int recentEntryCharLimit,
            boolean includeSimilarEntries
    ) {
        String rendered = buildContextString(date, dayOfWeek, season, daysJournaled,
                goodMoodCount, badMoodCount, highEnergyCount, lowEnergyCount, lowStreak,
                lifeSnapshot, avoidQuestions, recentEntries, similarEntries,
                recentEntryCharLimit, includeSimilarEntries);

        if (rendered.length() <= maxContextChars) {
            return rendered;
        }

        // Strategy 1: Truncate recent entries further (to 200 chars)
        if (recentEntryCharLimit > 200) {
            rendered = buildContextString(date, dayOfWeek, season, daysJournaled,
                    goodMoodCount, badMoodCount, highEnergyCount, lowEnergyCount, lowStreak,
                    lifeSnapshot, avoidQuestions, recentEntries, similarEntries,
                    200, includeSimilarEntries);
            if (rendered.length() <= maxContextChars) {
                return rendered;
            }
        }

        // Strategy 2: Drop similar entries
        if (includeSimilarEntries && !similarEntries.isEmpty()) {
            rendered = buildContextString(date, dayOfWeek, season, daysJournaled,
                    goodMoodCount, badMoodCount, highEnergyCount, lowEnergyCount, lowStreak,
                    lifeSnapshot, avoidQuestions, recentEntries, similarEntries,
                    200, false);
            if (rendered.length() <= maxContextChars) {
                return rendered;
            }
        }

        // Strategy 3: Truncate recent entries down to 100 chars
        return buildContextString(date, dayOfWeek, season, daysJournaled,
                goodMoodCount, badMoodCount, highEnergyCount, lowEnergyCount, lowStreak,
                lifeSnapshot, avoidQuestions, recentEntries, similarEntries,
                100, false);
    }

    private String buildContextString(
            LocalDate date,
            String dayOfWeek,
            String season,
            int daysJournaled,
            int goodMoodCount,
            int badMoodCount,
            int highEnergyCount,
            int lowEnergyCount,
            int lowStreak,
            Optional<String> lifeSnapshot,
            List<PromptQuestionDto> avoidQuestions,
            List<EntryResponse> recentEntries,
            List<EntryResponse> similarEntries,
            int recentEntryLimit,
            boolean includeSimilar
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append("[FACTS & TRENDS]\n");
        sb.append("- Target Date: ").append(date).append("\n");
        sb.append("- Day of Week: ").append(dayOfWeek).append("\n");
        sb.append("- Season: ").append(season).append("\n");
        sb.append("- Days journaled in last 14 days: ").append(daysJournaled).append("\n");
        sb.append("- Mood counts in last 14 days: Good=").append(goodMoodCount)
                .append(", Bad=").append(badMoodCount).append("\n");
        sb.append("- Energy counts in last 14 days: High=").append(highEnergyCount)
                .append(", Low=").append(lowEnergyCount).append("\n");
        sb.append("- Current low streak: ").append(lowStreak)
                .append(" consecutive journaled day(s) ending with Low energy or Bad mood (lowStreak=").append(lowStreak).append(")\n");
        if (lowStreak >= 2) {
            sb.append("- NOTE: lowStreak is >= 2. Gentle mode is active.\n");
        }
        sb.append("\n");

        lifeSnapshot.ifPresent(snap -> {
            sb.append("[LIFE SNAPSHOT]\n").append(snap.strip()).append("\n\n");
        });

        if (!avoidQuestions.isEmpty()) {
            sb.append("[AVOID THESE RECENT QUESTIONS]\n");
            sb.append("Do not ask questions that repeat or closely mirror any of these:\n");
            for (PromptQuestionDto q : avoidQuestions) {
                sb.append("- ").append(q.text()).append("\n");
            }
            sb.append("\n");
        }

        if (includeSimilar && !similarEntries.isEmpty()) {
            sb.append("[RELEVANT PAST ENTRIES]\n");
            for (EntryResponse e : similarEntries) {
                sb.append(String.format("Date: %s | Mood: %s | Energy: %s\n%s\n---\n",
                        e.entryDate(), e.mood(), e.energy(), truncate(e.body(), 300)));
            }
            sb.append("\n");
        }

        if (!recentEntries.isEmpty()) {
            sb.append("[RECENT ENTRIES (BEFORE ").append(date).append(")]\n");
            for (EntryResponse e : recentEntries) {
                sb.append(String.format("Date: %s | Mood: %s | Energy: %s\n%s\n---\n",
                        e.entryDate(), e.mood(), e.energy(), truncate(e.body(), recentEntryLimit)));
            }
        }

        return sb.toString().strip();
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        String trimmed = text.strip();
        if (trimmed.length() <= maxLength) return trimmed;
        return trimmed.substring(0, maxLength) + "...";
    }
}
