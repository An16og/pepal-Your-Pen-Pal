package local.pepal.journal.service;

import local.pepal.journal.ai.PersonaPromptBuilder;
import local.pepal.journal.ai.PersonaPromptBuilder.TaskType;
import local.pepal.journal.api.JournalDtos.EntryResponse;
import local.pepal.journal.api.JournalDtos.SettingsRecord;
import local.pepal.journal.api.JournalDtos.SummaryResponse;
import local.pepal.journal.api.JournalDtos.SummaryStats;
import local.pepal.journal.repository.JournalRepository;
import local.pepal.journal.repository.SummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SummaryServiceTest {

    @Mock
    private SummaryRepository summaryRepository;

    @Mock
    private JournalRepository journalRepository;

    @Mock
    private SettingsService settingsService;

    @Mock
    private PersonaPromptBuilder promptBuilder;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private SummaryService summaryService;

    @BeforeEach
    void setUp() {
        summaryService = new SummaryService(summaryRepository, journalRepository, settingsService, promptBuilder, chatClient);
    }

    @Test
    void generateSummary_whenFutureDate_throwsBadRequest() {
        LocalDate futureDate = LocalDate.now().plusWeeks(2);
        assertThrows(ResponseStatusException.class, () ->
                summaryService.generateSummary("WEEK", futureDate, false));
    }

    @Test
    void generateSummary_whenNoEntries_throwsNotFound() {
        LocalDate targetDate = LocalDate.of(2026, 9, 15);
        when(journalRepository.findInDateRange(any(), any())).thenReturn(List.of());

        assertThrows(ResponseStatusException.class, () ->
                summaryService.generateSummary("WEEK", targetDate, false));
    }

    @Test
    void generateSummary_whenEntriesExist_createsSummarySuccessfully() {
        LocalDate targetDate = LocalDate.of(2026, 9, 15);
        OffsetDateTime now = OffsetDateTime.now();
        List<EntryResponse> entries = List.of(
                new EntryResponse(1L, "DAILY_PROMPT", LocalDate.of(2026, 9, 15), now, now, "HIGH", "GOOD", "Great day coding and walking."),
                new EntryResponse(2L, "FREEFORM", LocalDate.of(2026, 9, 16), now, now, "LOW", "BAD", "Felt tired today.")
        );
        when(journalRepository.findInDateRange(any(), any())).thenReturn(entries);
        when(settingsService.getSettings()).thenReturn(new SettingsRecord((short) 1, "GENTLE", null, "EN", null, now));
        when(promptBuilder.build(any(), eq(TaskType.SUMMARY))).thenReturn("Summary persona prompt");

        String aiResponse = """
                HEADLINE: Steady Growth
                THEMES: Coding, Rest
                You had a balanced week where high focus met restful evenings.
                """;
        when(chatClient.prompt().system(anyString()).user(anyString()).options(any()).call().content())
                .thenReturn(aiResponse);

        SummaryStats stats = new SummaryStats(2, 7, 2, 2, 1, 1, 10);
        SummaryResponse expected = new SummaryResponse(
                1L, "WEEK", LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20),
                "Steady Growth", "You had a balanced week where high focus met restful evenings.",
                List.of("Coding", "Rest"), stats, "AI", true, false, now, now
        );
        when(summaryRepository.upsert(eq("WEEK"), any(), any(), eq("Steady Growth"), anyString(), anyList(), any(), eq("AI"), anyBoolean(), eq(false)))
                .thenReturn(expected);

        SummaryResponse result = summaryService.generateSummary("WEEK", targetDate, false);

        assertNotNull(result);
        assertEquals("Steady Growth", result.headline());
        assertEquals("AI", result.source());
    }

    @Test
    void generateSummary_whenYearly_calculatesFullYearPeriod() {
        LocalDate targetDate = LocalDate.of(2026, 5, 10);
        OffsetDateTime now = OffsetDateTime.now();
        List<EntryResponse> entries = List.of(
                new EntryResponse(1L, "DAILY_PROMPT", LocalDate.of(2026, 5, 10), now, now, "HIGH", "GOOD", "Yearly memory entry.")
        );
        when(journalRepository.findInDateRange(eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2027, 1, 1)))).thenReturn(entries);
        when(settingsService.getSettings()).thenReturn(new SettingsRecord((short) 1, "GENTLE", null, "EN", null, now));
        when(promptBuilder.build(any(), eq(TaskType.SUMMARY))).thenReturn("Summary persona prompt");
        when(chatClient.prompt().system(anyString()).user(anyString()).options(any()).call().content())
                .thenReturn("HEADLINE: Year of Reflection\nTHEMES: Growth\nA deep year.");

        SummaryStats stats = new SummaryStats(1, 365, 1, 1, 1, 1, 4);
        SummaryResponse expected = new SummaryResponse(
                2L, "YEAR", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                "Year of Reflection", "A deep year.", List.of("Growth"), stats, "AI", false, false, now, now
        );
        when(summaryRepository.upsert(eq("YEAR"), eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 12, 31)), anyString(), anyString(), anyList(), any(), anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(expected);

        SummaryResponse result = summaryService.generateSummary("YEAR", targetDate, false);

        assertNotNull(result);
        assertEquals("YEAR", result.periodType());
    }
}
