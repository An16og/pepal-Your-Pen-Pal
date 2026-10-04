package local.pepal.journal.service;

import local.pepal.journal.api.JournalDtos.EntryResponse;
import local.pepal.journal.api.JournalDtos.SaveEntryRequest;
import local.pepal.journal.api.JournalDtos.TrashEntryResponse;
import local.pepal.journal.api.JournalDtos.UpdateEntryRequest;
import local.pepal.journal.api.JournalExceptions.EntryNotFoundException;
import local.pepal.journal.api.JournalExceptions.RestoreConflictException;
import local.pepal.journal.repository.JournalRepository;
import local.pepal.journal.repository.JournalRepository.FullEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JournalServiceTest {

    @Mock
    private JournalRepository repository;

    @Mock
    private EmbeddingModel embeddingModel;

    private JournalService service;

    @BeforeEach
    void setUp() {
        service = new JournalService(repository, embeddingModel, 30);
    }

    @Test
    void save_embedsAndInsertsEntry() {
        LocalDate date = LocalDate.of(2026, 10, 3);
        SaveEntryRequest request = new SaveEntryRequest(
                "  Reflecting on today.  ",
                "DAILY_PROMPT",
                date,
                "HIGH",
                "GOOD"
        );
        String normalized = "Reflecting on today.";
        float[] fakeVector = new float[768];
        OffsetDateTime now = OffsetDateTime.now();
        EntryResponse expected = new EntryResponse(1L, "DAILY_PROMPT", date, now, now, "HIGH", "GOOD", normalized);

        when(repository.hasDailyEntryForDate(date)).thenReturn(false);
        when(embeddingModel.embed(anyString())).thenReturn(fakeVector);
        when(repository.insert(eq(normalized), eq("DAILY_PROMPT"), eq(date), eq("HIGH"), eq("GOOD"), eq(fakeVector)))
                .thenReturn(expected);

        EntryResponse actual = service.save(request);

        assertEquals(expected, actual);
        verify(repository).insert(normalized, "DAILY_PROMPT", date, "HIGH", "GOOD", fakeVector);
    }

    @Test
    void save_duplicateDailyEntryThrowsConflict() {
        LocalDate date = LocalDate.of(2026, 10, 3);
        SaveEntryRequest request = new SaveEntryRequest(
                "Second reflection",
                "DAILY_PROMPT",
                date,
                "LOW",
                "BAD"
        );
        when(repository.hasDailyEntryForDate(date)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.save(request));
    }

    @Test
    void update_updatesFieldsAndEmbedding() {
        LocalDate date = LocalDate.of(2026, 10, 3);
        OffsetDateTime now = OffsetDateTime.now();
        EntryResponse existing = new EntryResponse(1L, "FREEFORM", date, now, now, "HIGH", "GOOD", "Original");
        UpdateEntryRequest updateReq = new UpdateEntryRequest("Edited body", "LOW", "BAD", date, "FREEFORM");
        float[] fakeVector = new float[768];
        EntryResponse updated = new EntryResponse(1L, "FREEFORM", date, now, now, "LOW", "BAD", "Edited body");

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(embeddingModel.embed(anyString())).thenReturn(fakeVector);
        when(repository.update(1L, "Edited body", "LOW", "BAD", fakeVector)).thenReturn(updated);

        EntryResponse actual = service.update(1L, updateReq);

        assertEquals(updated, actual);
        verify(repository).update(1L, "Edited body", "LOW", "BAD", fakeVector);
    }

    @Test
    void update_rejectsImmutableDateChange() {
        LocalDate date = LocalDate.of(2026, 10, 3);
        OffsetDateTime now = OffsetDateTime.now();
        EntryResponse existing = new EntryResponse(1L, "FREEFORM", date, now, now, "HIGH", "GOOD", "Original");
        UpdateEntryRequest updateReq = new UpdateEntryRequest("Edited body", "LOW", "BAD", LocalDate.of(2026, 10, 4), "FREEFORM");

        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> service.update(1L, updateReq));
    }

    @Test
    void softDelete_callsRepository() {
        when(repository.softDelete(1L)).thenReturn(true);

        service.softDelete(1L);

        verify(repository).softDelete(1L);
    }

    @Test
    void softDelete_whenNotFound_throwsNotFound() {
        when(repository.softDelete(99L)).thenReturn(false);

        assertThrows(EntryNotFoundException.class, () -> service.softDelete(99L));
    }

    @Test
    void trash_calculatesDaysRemaining() {
        LocalDate date = LocalDate.of(2026, 10, 3);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime deletedAt = now.minusDays(5);
        FullEntry full = new FullEntry(1L, "FREEFORM", date, now, now, deletedAt, "HIGH", "GOOD", "Trashed");

        when(repository.findTrash()).thenReturn(List.of(full));

        List<TrashEntryResponse> trash = service.trash();

        assertEquals(1, trash.size());
        assertEquals(25L, trash.get(0).daysRemaining());
    }

    @Test
    void restore_whenConflictingDailyEntryExists_throwsConflict() {
        LocalDate date = LocalDate.of(2026, 10, 3);
        OffsetDateTime now = OffsetDateTime.now();
        FullEntry trashed = new FullEntry(1L, "DAILY_PROMPT", date, now, now, now, "HIGH", "GOOD", "Old daily");

        when(repository.findByIdIncludeDeleted(1L)).thenReturn(Optional.of(trashed));
        when(repository.findConflictingDailyEntryId(date)).thenReturn(Optional.of(2L));

        assertThrows(RestoreConflictException.class, () -> service.restore(1L));
    }

    @Test
    void permanentDelete_removesFromRepository() {
        LocalDate date = LocalDate.of(2026, 10, 3);
        OffsetDateTime now = OffsetDateTime.now();
        FullEntry trashed = new FullEntry(1L, "FREEFORM", date, now, now, now, "HIGH", "GOOD", "Trashed");

        when(repository.findByIdIncludeDeleted(1L)).thenReturn(Optional.of(trashed));

        service.permanentDelete(1L);

        verify(repository).permanentDelete(1L);
    }

    @Test
    void history_delegatesToRepository() {
        LocalDate date = LocalDate.now();
        OffsetDateTime now = OffsetDateTime.now();
        List<EntryResponse> entries = List.of(new EntryResponse(1L, "FREEFORM", date, now, now, "HIGH", "GOOD", "Entry 1"));
        when(repository.findAll()).thenReturn(entries);

        List<EntryResponse> actual = service.history();

        assertEquals(entries, actual);
        verify(repository).findAll();
    }

    @Test
    void relevantEntries_embedsQueryAndQueriesTop5() {
        String query = "When did I feel happy?";
        float[] fakeVector = new float[768];
        List<EntryResponse> entries = List.of(new EntryResponse(1L, "FREEFORM", LocalDate.now(), OffsetDateTime.now(), OffsetDateTime.now(), "HIGH", "GOOD", "Happy day"));

        when(embeddingModel.embed(query)).thenReturn(fakeVector);
        when(repository.findSimilar(fakeVector, 5)).thenReturn(entries);

        List<EntryResponse> actual = service.relevantEntries(query);

        assertEquals(entries, actual);
        verify(embeddingModel).embed(query);
        verify(repository).findSimilar(fakeVector, 5);
    }
}
