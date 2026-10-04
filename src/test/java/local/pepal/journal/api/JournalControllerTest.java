package local.pepal.journal.api;

import local.pepal.journal.api.JournalDtos.ChatResponse;
import local.pepal.journal.api.JournalDtos.EntryResponse;
import local.pepal.journal.api.JournalDtos.SaveEntryRequest;
import local.pepal.journal.api.JournalDtos.TrashEntryResponse;
import local.pepal.journal.api.JournalDtos.UpdateEntryRequest;
import local.pepal.journal.api.JournalExceptions.EntryNotFoundException;
import local.pepal.journal.api.JournalExceptions.RestoreConflictException;
import local.pepal.journal.service.JournalChatService;
import local.pepal.journal.service.JournalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class JournalControllerTest {

    @Mock
    private JournalService journalService;

    @Mock
    private JournalChatService chatService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JournalController controller = new JournalController(journalService, chatService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getEntries_returnsEntriesList() throws Exception {
        LocalDate today = LocalDate.now();
        OffsetDateTime now = OffsetDateTime.now();
        when(journalService.history()).thenReturn(List.of(
                new EntryResponse(1L, "FREEFORM", today, now, now, "HIGH", "GOOD", "Had a productive morning.")
        ));

        mockMvc.perform(get("/api/entries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].entryType").value("FREEFORM"))
                .andExpect(jsonPath("$[0].energy").value("HIGH"))
                .andExpect(jsonPath("$[0].mood").value("GOOD"))
                .andExpect(jsonPath("$[0].body").value("Had a productive morning."));
    }

    @Test
    void saveEntry_createsEntrySuccessfully() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 3);
        OffsetDateTime now = OffsetDateTime.now();
        when(journalService.save(any(SaveEntryRequest.class))).thenReturn(
                new EntryResponse(2L, "DAILY_PROMPT", date, now, now, "LOW", "GOOD", "Grateful for a quiet walk.")
        );

        mockMvc.perform(post("/api/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "body": "Grateful for a quiet walk.",
                                    "entryType": "DAILY_PROMPT",
                                    "entryDate": "2026-10-03",
                                    "energy": "LOW",
                                    "mood": "GOOD"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.entryType").value("DAILY_PROMPT"))
                .andExpect(jsonPath("$.energy").value("LOW"))
                .andExpect(jsonPath("$.body").value("Grateful for a quiet walk."));
    }

    @Test
    void saveEntry_rejectsBlankBody() throws Exception {
        mockMvc.perform(post("/api/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "body": "   ",
                                    "entryDate": "2026-10-03"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void saveEntry_whenDailyLimitReached_returnsConflict() throws Exception {
        when(journalService.save(any(SaveEntryRequest.class)))
                .thenThrow(new IllegalStateException("A Daily Reflection already exists for this date."));

        mockMvc.perform(post("/api/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "body": "Second reflection",
                                    "entryType": "DAILY_PROMPT",
                                    "entryDate": "2026-10-03",
                                    "energy": "HIGH",
                                    "mood": "GOOD"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A Daily Reflection already exists for this date."));
    }

    @Test
    void updateEntry_updatesSuccessfully() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 3);
        OffsetDateTime now = OffsetDateTime.now();
        when(journalService.update(eq(1L), any(UpdateEntryRequest.class))).thenReturn(
                new EntryResponse(1L, "FREEFORM", date, now, now, "LOW", "BAD", "Edited text")
        );

        mockMvc.perform(put("/api/entries/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "body": "Edited text",
                                    "energy": "LOW",
                                    "mood": "BAD"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Edited text"))
                .andExpect(jsonPath("$.energy").value("LOW"));
    }

    @Test
    void updateEntry_whenNotFound_returns404() throws Exception {
        when(journalService.update(eq(99L), any(UpdateEntryRequest.class)))
                .thenThrow(new EntryNotFoundException("Entry not found or already in trash: 99"));

        mockMvc.perform(put("/api/entries/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "body": "Edited text",
                                    "energy": "LOW",
                                    "mood": "BAD"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Entry not found or already in trash: 99"));
    }

    @Test
    void softDelete_returns204() throws Exception {
        doNothing().when(journalService).softDelete(1L);

        mockMvc.perform(delete("/api/entries/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void softDelete_whenNotFound_returns404() throws Exception {
        doThrow(new EntryNotFoundException("Entry not found or already in trash: 99"))
                .when(journalService).softDelete(99L);

        mockMvc.perform(delete("/api/entries/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTrash_returnsTrashedEntries() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 3);
        OffsetDateTime now = OffsetDateTime.now();
        when(journalService.trash()).thenReturn(List.of(
                new TrashEntryResponse(1L, "FREEFORM", date, now, now, now, 30L, "HIGH", "GOOD", "Trashed entry")
        ));

        mockMvc.perform(get("/api/trash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].daysRemaining").value(30));
    }

    @Test
    void restore_whenSuccessful_returns200() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 3);
        OffsetDateTime now = OffsetDateTime.now();
        when(journalService.restore(1L)).thenReturn(
                new EntryResponse(1L, "FREEFORM", date, now, now, "HIGH", "GOOD", "Restored")
        );

        mockMvc.perform(post("/api/entries/1/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void restore_whenConflict_returns409WithConflictingId() throws Exception {
        when(journalService.restore(1L))
                .thenThrow(new RestoreConflictException(5L, "Cannot restore daily reflection: another active entry (5) already exists"));

        mockMvc.perform(post("/api/entries/1/restore"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflictingEntryId").value(5));
    }

    @Test
    void permanentDelete_returns204() throws Exception {
        doNothing().when(journalService).permanentDelete(1L);

        mockMvc.perform(delete("/api/entries/1/permanent"))
                .andExpect(status().isNoContent());
    }

    @Test
    void emptyTrash_returns204() throws Exception {
        when(journalService.emptyTrash()).thenReturn(3);

        mockMvc.perform(delete("/api/trash"))
                .andExpect(status().isNoContent());
    }

    @Test
    void chat_returnsAnswerSuccessfully() throws Exception {
        when(chatService.ask("What went well?")).thenReturn(
                new ChatResponse("You mentioned having a productive morning.")
        );

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"What went well?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("You mentioned having a productive morning."));
    }

    @Test
    void chat_rejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void databaseUnavailable_returnsServiceUnavailable() throws Exception {
        when(journalService.history()).thenThrow(new DataAccessException("Connection failed") {});

        mockMvc.perform(get("/api/entries"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Local database is unavailable. Start PostgreSQL and try again."));
    }

    @Test
    void ollamaUnavailable_returnsServiceUnavailable() throws Exception {
        when(journalService.save(any(SaveEntryRequest.class))).thenThrow(new RestClientException("Connection refused"));

        mockMvc.perform(post("/api/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "body": "Journaling today.",
                                    "entryDate": "2026-10-03"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Local Ollama service is unavailable. Please ensure 'ollama serve' is running."));
    }
}
