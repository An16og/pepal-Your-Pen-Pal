package local.pepal.journal.service;

import local.pepal.journal.ai.PersonaPromptBuilder;
import local.pepal.journal.ai.PersonaPromptBuilder.TaskType;
import local.pepal.journal.api.JournalDtos.ChatResponse;
import local.pepal.journal.api.JournalDtos.EntryResponse;
import local.pepal.journal.api.JournalDtos.SettingsRecord;
import local.pepal.journal.repository.JournalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JournalChatServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private JournalService journalService;

    @Mock
    private JournalRepository journalRepository;

    @Mock
    private SettingsService settingsService;

    @Mock
    private PersonaPromptBuilder promptBuilder;

    private JournalChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new JournalChatService(chatClient, journalService, journalRepository, settingsService, promptBuilder);
    }

    @Test
    void ask_whenNoEntriesFound_returnsFallbackWithoutCallingChatClient() {
        when(journalRepository.findRecentBefore(any(), eq(5))).thenReturn(List.of());
        when(journalService.relevantEntries("What did I eat?")).thenReturn(List.of());

        ChatResponse response = chatService.ask("What did I eat?");

        assertEquals("You haven't written any journal entries yet. Once you write your first reflection, I can help you spot patterns and reflect on your days.", response.answer());
        verifyNoInteractions(chatClient);
    }

    @Test
    void ask_whenEntriesFound_promptsChatClientAndReturnsGroundedAnswer() {
        OffsetDateTime now = OffsetDateTime.now();
        List<EntryResponse> entries = List.of(
                new EntryResponse(1L, "DAILY_PROMPT", LocalDate.of(2026, 10, 2), now, now, "HIGH", "GOOD", "Went for an early run in the park.")
        );
        SettingsRecord settings = new SettingsRecord((short) 1, "GENTLE", null, "EN", null, now);
        when(journalService.relevantEntries("Did I exercise?")).thenReturn(entries);
        when(settingsService.getSettings()).thenReturn(settings);
        when(promptBuilder.build(eq(settings), eq(TaskType.CHAT))).thenReturn("Custom system prompt with gentle tone");
        when(chatClient.prompt().system(anyString()).user(anyString()).options(any()).call().content())
                .thenReturn("Yes, you went for an early run in the park on October 2nd.");

        ChatResponse response = chatService.ask("Did I exercise?");

        assertEquals("Yes, you went for an early run in the park on October 2nd.", response.answer());
    }

    @Test
    void ask_whenModelReturnsBlank_returnsInsufficientInformationMessage() {
        OffsetDateTime now = OffsetDateTime.now();
        List<EntryResponse> entries = List.of(
                new EntryResponse(1L, "FREEFORM", LocalDate.of(2026, 10, 2), now, now, "LOW", "BAD", "Wrote some code today.")
        );
        SettingsRecord settings = new SettingsRecord((short) 1, "COACH", null, "HINGLISH", null, now);
        when(journalService.relevantEntries("Did I exercise?")).thenReturn(entries);
        when(settingsService.getSettings()).thenReturn(settings);
        when(promptBuilder.build(eq(settings), eq(TaskType.CHAT))).thenReturn("Coach system prompt");
        when(chatClient.prompt().system(anyString()).user(anyString()).options(any()).call().content())
                .thenReturn("   ");

        ChatResponse response = chatService.ask("Did I exercise?");

        assertEquals("The journal does not contain enough information to answer that.", response.answer());
    }
}
