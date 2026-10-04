package local.pepal.journal;

import local.pepal.journal.api.JournalDtos.EntryResponse;
import local.pepal.journal.api.JournalDtos.SaveEntryRequest;
import local.pepal.journal.api.JournalDtos.UpdateEntryRequest;
import local.pepal.journal.repository.JournalRepository;
import local.pepal.journal.service.JournalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class JournalIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    );

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        }
    }

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @MockitoBean
    private ChatModel chatModel;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JournalService journalService;

    @Autowired
    private JournalRepository journalRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for Testcontainers integration test");
        float[] mockVector = new float[768];
        mockVector[0] = 0.5f;
        when(embeddingModel.embed(anyString())).thenReturn(mockVector);

        jdbcTemplate.update("DELETE FROM journal_entry");
        jdbcTemplate.update("DELETE FROM daily_prompt");
        jdbcTemplate.update("UPDATE settings SET persona_preset = 'GENTLE', custom_persona = NULL, language = 'EN' WHERE id = 1");
    }

    @Test
    void editUpdatesFieldsAndReEmbeds() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 3);
        SaveEntryRequest saveReq = new SaveEntryRequest("Original text", "FREEFORM", date, "HIGH", "GOOD");
        EntryResponse created = journalService.save(saveReq);

        float[] newVector = new float[768];
        newVector[0] = 0.9f;
        when(embeddingModel.embed(anyString())).thenReturn(newVector);

        mockMvc.perform(put("/api/entries/" + created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "body": "Edited text content",
                                    "energy": "LOW",
                                    "mood": "BAD"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.id()))
                .andExpect(jsonPath("$.body").value("Edited text content"))
                .andExpect(jsonPath("$.energy").value("LOW"))
                .andExpect(jsonPath("$.mood").value("BAD"));

        EntryResponse reloaded = journalService.get(created.id());
        assertEquals("Edited text content", reloaded.body());
        assertEquals("LOW", reloaded.energy());
        assertEquals("BAD", reloaded.mood());
    }

    @Test
    void softDeleteHidesFromEntriesAndShowsInTrash() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 3);
        EntryResponse entry = journalService.save(new SaveEntryRequest("To be deleted", "FREEFORM", date, "HIGH", "GOOD"));

        mockMvc.perform(delete("/api/entries/" + entry.id()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/entries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/trash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(entry.id()))
                .andExpect(jsonPath("$[0].deletedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].daysRemaining").value(30));
    }

    @Test
    void deletingDailyReflectionAllowsCreatingNewOneForSameDate() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 3);
        EntryResponse first = journalService.save(new SaveEntryRequest("First reflection", "DAILY_PROMPT", date, "HIGH", "GOOD"));

        mockMvc.perform(post("/api/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "body": "Duplicate reflection attempt",
                                    "entryType": "DAILY_PROMPT",
                                    "entryDate": "2026-10-03",
                                    "energy": "HIGH",
                                    "mood": "GOOD"
                                }
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/entries/" + first.id()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "body": "New daily reflection for same date",
                                    "entryType": "DAILY_PROMPT",
                                    "entryDate": "2026-10-03",
                                    "energy": "LOW",
                                    "mood": "GOOD"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("New daily reflection for same date"));
    }

    @Test
    void restoreSucceedsWhenNoConflict_andReturns409WhenConflict() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 3);
        EntryResponse entryA = journalService.save(new SaveEntryRequest("Daily A", "DAILY_PROMPT", date, "HIGH", "GOOD"));
        journalService.softDelete(entryA.id());

        mockMvc.perform(post("/api/entries/" + entryA.id() + "/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(entryA.id()));

        journalService.softDelete(entryA.id());

        EntryResponse entryB = journalService.save(new SaveEntryRequest("Daily B", "DAILY_PROMPT", date, "HIGH", "GOOD"));

        mockMvc.perform(post("/api/entries/" + entryA.id() + "/restore"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflictingEntryId").value(entryB.id()));
    }

    @Test
    void permanentDeleteCascadesToEntryEmbedding() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 3);
        EntryResponse entry = journalService.save(new SaveEntryRequest("Permanent test", "FREEFORM", date, "HIGH", "GOOD"));

        Integer countBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM entry_embedding WHERE entry_id = ?", Integer.class, entry.id());
        assertEquals(1, countBefore);

        journalService.softDelete(entry.id());

        mockMvc.perform(delete("/api/entries/" + entry.id() + "/permanent"))
                .andExpect(status().isNoContent());

        Integer countEntry = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM journal_entry WHERE id = ?", Integer.class, entry.id());
        assertEquals(0, countEntry);

        Integer countEmbedding = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM entry_embedding WHERE entry_id = ?", Integer.class, entry.id());
        assertEquals(0, countEmbedding);
    }

    @Test
    void trashedEntriesNeverAppearInRagRetrieval() {
        LocalDate date = LocalDate.of(2026, 10, 3);
        EntryResponse entry = journalService.save(new SaveEntryRequest("QuantumSecretObservation", "FREEFORM", date, "HIGH", "GOOD"));

        List<EntryResponse> beforeDelete = journalService.relevantEntries("QuantumSecretObservation");
        assertEquals(1, beforeDelete.size());
        assertEquals(entry.id(), beforeDelete.get(0).id());

        journalService.softDelete(entry.id());

        List<EntryResponse> afterDelete = journalService.relevantEntries("QuantumSecretObservation");
        assertTrue(afterDelete.isEmpty(), "Trashed entry must never appear in RAG retrieval");
    }

    @Test
    void purgeJobRemovesOnlyEntriesPastRetention() {
        LocalDate date = LocalDate.of(2026, 10, 3);
        EntryResponse oldEntry = journalService.save(new SaveEntryRequest("Old trashed entry", "FREEFORM", date, "HIGH", "GOOD"));
        EntryResponse recentEntry = journalService.save(new SaveEntryRequest("Recent trashed entry", "FREEFORM", date, "HIGH", "GOOD"));

        journalService.softDelete(oldEntry.id());
        journalService.softDelete(recentEntry.id());

        jdbcTemplate.update("UPDATE journal_entry SET deleted_at = CURRENT_TIMESTAMP - INTERVAL '40 days' WHERE id = ?", oldEntry.id());
        jdbcTemplate.update("UPDATE journal_entry SET deleted_at = CURRENT_TIMESTAMP - INTERVAL '5 days' WHERE id = ?", recentEntry.id());

        int purged = journalService.purgeOldTrash();
        assertEquals(1, purged);

        Integer oldExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM journal_entry WHERE id = ?", Integer.class, oldEntry.id());
        assertEquals(0, oldExists);

        Integer recentExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM journal_entry WHERE id = ?", Integer.class, recentEntry.id());
        assertEquals(1, recentExists);
    }

    @Test
    void getSettingsReturnsDefaultsOnFreshDb() throws Exception {
        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personaPreset").value("GENTLE"))
                .andExpect(jsonPath("$.language").value("EN"))
                .andExpect(jsonPath("$.availablePresets.GENTLE").isNotEmpty());
    }

    @Test
    void putSettingsPersistsAndSubsequentGetReflectsIt() throws Exception {
        mockMvc.perform(put("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "personaPreset": "COACH",
                                    "language": "HINGLISH"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personaPreset").value("COACH"))
                .andExpect(jsonPath("$.language").value("HINGLISH"));

        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personaPreset").value("COACH"))
                .andExpect(jsonPath("$.language").value("HINGLISH"));
    }

    @Test
    void putCustomWithBlankTextReturns400() throws Exception {
        mockMvc.perform(put("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "personaPreset": "CUSTOM",
                                    "customPersona": "   ",
                                    "language": "EN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("customPersona is required and cannot be blank when preset is CUSTOM"));
    }

    @Test
    void chatUsesSystemPromptWithSelectedPersona() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 3);
        journalService.save(new SaveEntryRequest("Learning and reflecting today.", "FREEFORM", date, "HIGH", "GOOD"));

        mockMvc.perform(put("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "personaPreset": "COACH",
                                    "language": "EN"
                                }
                                """))
                .andExpect(status().isOk());

        org.springframework.ai.chat.model.ChatResponse mockAiResponse =
                mock(org.springframework.ai.chat.model.ChatResponse.class, RETURNS_DEEP_STUBS);
        when(mockAiResponse.getResult().getOutput().getText()).thenReturn("Coach grounded reply");
        when(chatModel.call(any(Prompt.class))).thenReturn(mockAiResponse);

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"What should I focus on?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Coach grounded reply"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        String capturedText = captor.getValue().getInstructions().toString();

        assertTrue(capturedText.contains("Your tone is forward-looking, empowering, and action-oriented"),
                "Prompt must contain Coach persona instructions");
        assertTrue(capturedText.contains("[CRITICAL SAFETY & ETHICAL BOUNDARIES - STRICT PRECEDENCE]"),
                "Prompt must contain Safety layer instructions");
    }

    @Test
    void getTodayPrompt_coldStart_persistsAndReturnsFallback() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 3);

        mockMvc.perform(get("/api/prompts/today?date=" + date))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-10-03"))
                .andExpect(jsonPath("$.source").value("FALLBACK"))
                .andExpect(jsonPath("$.reshufflesRemaining").value(1))
                .andExpect(jsonPath("$.questions.length()").value(3));

        // Row persisted in database
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_prompt WHERE prompt_date = ?", Integer.class, date);
        assertEquals(1, count);

        // Fetching again returns cached row without calling chat model
        mockMvc.perform(get("/api/prompts/today?date=" + date))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("FALLBACK"));

        verifyNoInteractions(chatModel);
    }

    @Test
    void reshuffle_onFallback_isFree() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 3);

        // First call creates fallback prompt
        mockMvc.perform(get("/api/prompts/today?date=" + date))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("FALLBACK"));

        // Reshuffle on fallback is free (does not throw 409)
        mockMvc.perform(post("/api/prompts/reshuffle?date=" + date))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("FALLBACK"))
                .andExpect(jsonPath("$.reshufflesRemaining").value(1));
    }
}
