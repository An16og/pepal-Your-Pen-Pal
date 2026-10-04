package local.pepal.journal.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class JournalDtos {
    private JournalDtos() {}

    public record SaveEntryRequest(
            @NotBlank @Size(max = 30000) String body,
            @Pattern(regexp = "DAILY_PROMPT|FREEFORM") String entryType,
            @NotNull LocalDate entryDate,
            @Pattern(regexp = "HIGH|LOW") String energy,
            @Pattern(regexp = "GOOD|BAD") String mood
    ) {
        public String effectiveType() {
            return entryType != null && !entryType.isBlank() ? entryType : "FREEFORM";
        }
        public String effectiveEnergy() {
            return energy != null && !energy.isBlank() ? energy : "HIGH";
        }
        public String effectiveMood() {
            return mood != null && !mood.isBlank() ? mood : "GOOD";
        }
    }

    public record UpdateEntryRequest(
            @NotBlank @Size(max = 30000) String body,
            @Pattern(regexp = "HIGH|LOW") String energy,
            @Pattern(regexp = "GOOD|BAD") String mood,
            LocalDate entryDate,
            String entryType
    ) {
        public String effectiveEnergy() {
            return energy != null && !energy.isBlank() ? energy : "HIGH";
        }
        public String effectiveMood() {
            return mood != null && !mood.isBlank() ? mood : "GOOD";
        }
    }

    public record EntryResponse(
            long id,
            String entryType,
            LocalDate entryDate,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String energy,
            String mood,
            String body
    ) {}

    public record TrashEntryResponse(
            long id,
            String entryType,
            LocalDate entryDate,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime deletedAt,
            long daysRemaining,
            String energy,
            String mood,
            String body
    ) {}

    public record SettingsRecord(
            short id,
            String personaPreset,
            String customPersona,
            String language,
            String userName,
            OffsetDateTime updatedAt
    ) {}

    public record SettingsResponse(
            String personaPreset,
            String customPersona,
            String language,
            String userName,
            OffsetDateTime updatedAt,
            Map<String, String> availablePresets
    ) {}

    public record UpdateSettingsRequest(
            @NotBlank String personaPreset,
            @Size(max = 500) String customPersona,
            @NotBlank String language,
            @Size(max = 100) String userName
    ) {}

    public record PromptPreviewResponse(
            String personaPreset,
            String language,
            String systemPrompt
    ) {}

    public record PromptQuestionDto(
            String text,
            String kind
    ) {}

    public record DailyPromptRecord(
            LocalDate promptDate,
            List<PromptQuestionDto> questions,
            String source,
            short reshuffleCount,
            OffsetDateTime createdAt
    ) {}

    public record DailyPromptResponse(
            LocalDate date,
            List<PromptQuestionDto> questions,
            String source,
            int reshufflesRemaining
    ) {}

    public record ChatMessageDto(String role, String content) {}
    public record ChatRequest(
            @NotBlank @Size(max = 4000) String message,
            List<ChatMessageDto> history
    ) {}
    public record ChatResponse(String answer) {}

    public record SummaryStats(
            int daysJournaled,
            int daysInPeriod,
            int totalEntries,
            int longestStreak,
            int goodMoodDays,
            int highEnergyDays,
            int totalWords
    ) {}

    public record SummaryResponse(
            long id,
            String periodType,
            LocalDate periodStart,
            LocalDate periodEnd,
            String headline,
            String body,
            List<String> themes,
            SummaryStats stats,
            String source,
            boolean isFinal,
            boolean stale,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}
}
