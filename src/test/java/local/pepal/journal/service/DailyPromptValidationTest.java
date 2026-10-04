package local.pepal.journal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import local.pepal.journal.ai.PersonaPromptBuilder;
import local.pepal.journal.api.JournalDtos.EntryResponse;
import local.pepal.journal.api.JournalDtos.PromptQuestionDto;
import local.pepal.journal.repository.DailyPromptRepository;
import local.pepal.journal.repository.JournalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyPromptValidationTest {

    @Mock
    private DailyPromptRepository dailyPromptRepository;
    @Mock
    private PromptContextAssembler contextAssembler;
    @Mock
    private SettingsService settingsService;
    @Mock
    private PersonaPromptBuilder promptBuilder;
    @Mock
    private ChatClient chatClient;
    @Mock
    private JournalRepository journalRepository;
    @Mock
    private EmbeddingModel embeddingModel;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private FallbackPromptProvider fallbackPromptProvider;
    private DailyPromptService dailyPromptService;

    @BeforeEach
    void setUp() {
        fallbackPromptProvider = new FallbackPromptProvider(objectMapper);
        dailyPromptService = new DailyPromptService(
                dailyPromptRepository,
                contextAssembler,
                fallbackPromptProvider,
                settingsService,
                promptBuilder,
                chatClient,
                objectMapper,
                "llama3.2",
                0.8,
                20,
                0.7
        );
    }

    @Test
    void validQuestionsPassValidation() {
        List<PromptQuestionDto> raw = List.of(
                new PromptQuestionDto("What is a quiet moment you felt steady today?", "REFLECTIVE"),
                new PromptQuestionDto("If today was a fruit, what would it be?", "PLAYFUL"),
                new PromptQuestionDto("What is one gentle step you want to take tomorrow?", "FORWARD")
        );

        List<PromptQuestionDto> normalized = dailyPromptService.validateAndNormalizeQuestions(raw, List.of());
        assertEquals(3, normalized.size());
        assertEquals("REFLECTIVE", normalized.get(0).kind());
        assertEquals("PLAYFUL", normalized.get(1).kind());
        assertEquals("FORWARD", normalized.get(2).kind());
    }

    @Test
    void rejectsIncorrectQuestionCount() {
        List<PromptQuestionDto> twoQuestions = List.of(
                new PromptQuestionDto("What felt steady today?", "REFLECTIVE"),
                new PromptQuestionDto("What fruit are you?", "PLAYFUL")
        );
        assertThrows(IllegalArgumentException.class, () ->
                dailyPromptService.validateAndNormalizeQuestions(twoQuestions, List.of()));
    }

    @Test
    void rejectsDuplicateOrMissingKind() {
        List<PromptQuestionDto> duplicateKinds = List.of(
                new PromptQuestionDto("What felt steady today?", "REFLECTIVE"),
                new PromptQuestionDto("What made you think today?", "REFLECTIVE"),
                new PromptQuestionDto("What is next?", "FORWARD")
        );
        assertThrows(IllegalArgumentException.class, () ->
                dailyPromptService.validateAndNormalizeQuestions(duplicateKinds, List.of()));
    }

    @Test
    void rejectsQuestionsOver160Chars() {
        String longText = "What is a very very very very very very very very very very very very very very very very very very very very very very very very very very very long question here?";
        assertTrue(longText.length() > 160);

        List<PromptQuestionDto> raw = List.of(
                new PromptQuestionDto(longText, "REFLECTIVE"),
                new PromptQuestionDto("What fruit are you?", "PLAYFUL"),
                new PromptQuestionDto("What is next?", "FORWARD")
        );
        assertThrows(IllegalArgumentException.class, () ->
                dailyPromptService.validateAndNormalizeQuestions(raw, List.of()));
    }

    @Test
    void rejectsQuestionNotEndingInQuestionMark() {
        List<PromptQuestionDto> raw = List.of(
                new PromptQuestionDto("What felt steady today.", "REFLECTIVE"),
                new PromptQuestionDto("What fruit are you?", "PLAYFUL"),
                new PromptQuestionDto("What is next?", "FORWARD")
        );
        assertThrows(IllegalArgumentException.class, () ->
                dailyPromptService.validateAndNormalizeQuestions(raw, List.of()));
    }

    @Test
    void rejectsMultiSentenceQuestions() {
        List<PromptQuestionDto> raw = List.of(
                new PromptQuestionDto("Today was busy. How did you feel about that?", "REFLECTIVE"),
                new PromptQuestionDto("What fruit are you?", "PLAYFUL"),
                new PromptQuestionDto("What is next?", "FORWARD")
        );
        assertThrows(IllegalArgumentException.class, () ->
                dailyPromptService.validateAndNormalizeQuestions(raw, List.of()));
    }

    @Test
    void rejectsQuestionsWithHighTokenOverlap() {
        List<PromptQuestionDto> avoidList = List.of(
                new PromptQuestionDto("What is a quiet, small moment from today that felt steadying?", "REFLECTIVE")
        );

        // Near duplicate with > 0.7 Jaccard token overlap
        List<PromptQuestionDto> raw = List.of(
                new PromptQuestionDto("What is a quiet small moment today that felt steadying?", "REFLECTIVE"),
                new PromptQuestionDto("What fruit are you?", "PLAYFUL"),
                new PromptQuestionDto("What is next?", "FORWARD")
        );
        assertThrows(IllegalArgumentException.class, () ->
                dailyPromptService.validateAndNormalizeQuestions(raw, avoidList));
    }

    @Test
    void fallbackProviderIsDeterministicPerDate() {
        LocalDate date = LocalDate.of(2026, 10, 3);
        List<PromptQuestionDto> q1 = fallbackPromptProvider.getQuestionsForDate(date);
        List<PromptQuestionDto> q2 = fallbackPromptProvider.getQuestionsForDate(date);

        assertEquals(3, q1.size());
        assertEquals(q1.get(0).text(), q2.get(0).text());
        assertEquals(q1.get(1).text(), q2.get(1).text());
        assertEquals(q1.get(2).text(), q2.get(2).text());

        assertEquals("REFLECTIVE", q1.get(0).kind());
        assertEquals("PLAYFUL", q1.get(1).kind());
        assertEquals("FORWARD", q1.get(2).kind());
    }

    @Test
    void contextAssemblerBudgetTrimmingPreservesFactsSection() {
        PromptContextAssembler assembler = new PromptContextAssembler(
                journalRepository,
                dailyPromptRepository,
                embeddingModel,
                new NoOpLifeSnapshotProvider(),
                300 // very small budget to force aggressive trimming
        );

        LocalDate date = LocalDate.of(2026, 10, 3);
        when(journalRepository.countNonDeletedBefore(date)).thenReturn(5);
        when(journalRepository.findInDateRange(any(), any())).thenReturn(List.of(
                new EntryResponse(1L, "FREEFORM", date.minusDays(1), OffsetDateTime.now(), OffsetDateTime.now(), "LOW", "BAD", "Long text...")
        ));
        when(dailyPromptRepository.findRecentQuestions(any(), anyInt())).thenReturn(List.of());
        when(journalRepository.findRecentBefore(date, 5)).thenReturn(List.of(
                new EntryResponse(2L, "FREEFORM", date.minusDays(1), OffsetDateTime.now(), OffsetDateTime.now(), "LOW", "BAD", "A".repeat(500))
        ));

        PromptContextAssembler.PromptContext context = assembler.assemble(date, null);
        assertNotNull(context.renderedContext());
        assertTrue(context.renderedContext().contains("[FACTS & TRENDS]"), "Facts section must NEVER be dropped");
        assertTrue(context.renderedContext().contains("lowStreak="));
        assertEquals(1, context.lowStreak());
    }
}
