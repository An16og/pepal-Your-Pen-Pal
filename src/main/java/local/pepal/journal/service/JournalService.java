package local.pepal.journal.service;

import local.pepal.journal.api.JournalDtos.EntryResponse;
import local.pepal.journal.api.JournalDtos.SaveEntryRequest;
import local.pepal.journal.api.JournalDtos.TrashEntryResponse;
import local.pepal.journal.api.JournalDtos.UpdateEntryRequest;
import local.pepal.journal.api.JournalExceptions.EntryNotFoundException;
import local.pepal.journal.api.JournalExceptions.RestoreConflictException;
import local.pepal.journal.repository.JournalRepository;
import local.pepal.journal.repository.JournalRepository.FullEntry;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class JournalService {
    private final JournalRepository repository;
    private final EmbeddingModel embeddingModel;
    private final int retentionDays;

    public JournalService(
            JournalRepository repository,
            EmbeddingModel embeddingModel,
            @Value("${otto.trash.retention-days:${usher.trash.retention-days:30}}") int retentionDays
    ) {
        this.repository = repository;
        this.embeddingModel = embeddingModel;
        this.retentionDays = retentionDays;
    }

    public static String formatEmbeddingText(LocalDate date, String mood, String energy, String body) {
        return String.format("Date: %s | Mood: %s | Energy: %s\n%s", date, mood, energy, body);
    }

    public EntryResponse save(SaveEntryRequest request) {
        String normalized = request.body().strip();
        String type = request.effectiveType();
        LocalDate date = request.entryDate() != null ? request.entryDate() : LocalDate.now();
        String energy = request.effectiveEnergy();
        String mood = request.effectiveMood();

        if ("DAILY_PROMPT".equals(type) && repository.hasDailyEntryForDate(date)) {
            throw new IllegalStateException("A Daily Reflection already exists for " + date +
                    ". You can save this as a Freeform entry or pick another date.");
        }

        // Embedding is calculated outside DB transaction
        String textToEmbed = formatEmbeddingText(date, mood, energy, normalized);
        float[] embedding = embeddingModel.embed(textToEmbed);

        return saveInternal(normalized, type, date, energy, mood, embedding);
    }

    @Transactional
    protected EntryResponse saveInternal(String body, String type, LocalDate date, String energy, String mood, float[] embedding) {
        return repository.insert(body, type, date, energy, mood, embedding);
    }

    @Transactional(readOnly = true)
    public List<EntryResponse> history() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public EntryResponse get(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntryNotFoundException("Entry " + id + " not found or in trash"));
    }

    public EntryResponse update(long id, UpdateEntryRequest request) {
        EntryResponse existing = repository.findById(id)
                .orElseThrow(() -> new EntryNotFoundException("Entry not found or already in trash: " + id));

        if (request.entryDate() != null && !request.entryDate().equals(existing.entryDate())) {
            throw new IllegalArgumentException("entryDate is immutable and cannot be changed");
        }
        if (request.entryType() != null && !request.entryType().isBlank() && !request.entryType().equals(existing.entryType())) {
            throw new IllegalArgumentException("entryType is immutable and cannot be changed");
        }

        String normalized = request.body().strip();
        String energy = request.effectiveEnergy();
        String mood = request.effectiveMood();

        // Embedding is calculated outside DB transaction (fails fast if Ollama is unreachable)
        String textToEmbed = formatEmbeddingText(existing.entryDate(), mood, energy, normalized);
        float[] embedding = embeddingModel.embed(textToEmbed);

        return updateInternal(id, normalized, energy, mood, embedding);
    }

    @Transactional
    protected EntryResponse updateInternal(long id, String body, String energy, String mood, float[] embedding) {
        return repository.update(id, body, energy, mood, embedding);
    }

    @Transactional
    public void softDelete(long id) {
        boolean deleted = repository.softDelete(id);
        if (!deleted) {
            throw new EntryNotFoundException("Entry not found or already in trash: " + id);
        }
    }

    @Transactional(readOnly = true)
    public List<TrashEntryResponse> trash() {
        List<FullEntry> trashed = repository.findTrash();
        OffsetDateTime now = OffsetDateTime.now();

        return trashed.stream().map(entry -> {
            long daysPassed = Duration.between(entry.deletedAt(), now).toDays();
            long daysRemaining = Math.max(0, retentionDays - daysPassed);
            return new TrashEntryResponse(
                    entry.id(),
                    entry.entryType(),
                    entry.entryDate(),
                    entry.createdAt(),
                    entry.updatedAt(),
                    entry.deletedAt(),
                    daysRemaining,
                    entry.energy(),
                    entry.mood(),
                    entry.body()
            );
        }).toList();
    }

    @Transactional
    public EntryResponse restore(long id) {
        FullEntry entry = repository.findByIdIncludeDeleted(id)
                .orElseThrow(() -> new EntryNotFoundException("Entry not found: " + id));

        if (entry.deletedAt() == null) {
            throw new IllegalArgumentException("Entry is not in the trash: " + id);
        }

        if ("DAILY_PROMPT".equals(entry.entryType())) {
            Optional<Long> conflict = repository.findConflictingDailyEntryId(entry.entryDate());
            if (conflict.isPresent()) {
                throw new RestoreConflictException(conflict.get(),
                        "Cannot restore daily reflection: another active entry (" + conflict.get() + ") already exists for date " + entry.entryDate());
            }
        }

        try {
            return repository.restore(id);
        } catch (DataIntegrityViolationException ex) {
            // Map concurrent race constraint violation to 409
            Optional<Long> conflict = repository.findConflictingDailyEntryId(entry.entryDate());
            long conflictId = conflict.orElse(0L);
            throw new RestoreConflictException(conflictId,
                    "Cannot restore daily reflection: another active entry (" + conflictId + ") already exists for date " + entry.entryDate());
        }
    }

    @Transactional
    public void permanentDelete(long id) {
        FullEntry entry = repository.findByIdIncludeDeleted(id)
                .orElseThrow(() -> new EntryNotFoundException("Entry not found: " + id));

        if (entry.deletedAt() == null) {
            throw new IllegalStateException("Entry " + id + " is not in the trash and cannot be permanently deleted");
        }

        repository.permanentDelete(id);
    }

    @Transactional
    public int emptyTrash() {
        return repository.emptyTrash();
    }

    @Scheduled(cron = "${otto.trash.purge-cron:${usher.trash.purge-cron:0 0 2 * * ?}}")
    @Transactional
    public int purgeOldTrash() {
        return repository.purgeOldTrash(retentionDays);
    }

    public List<EntryResponse> relevantEntries(String question) {
        return repository.findSimilar(embeddingModel.embed(question), 5);
    }
}
