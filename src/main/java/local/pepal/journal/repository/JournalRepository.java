package local.pepal.journal.repository;

import local.pepal.journal.api.JournalDtos.EntryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JournalRepository {
    private final JdbcTemplate jdbc;

    public record FullEntry(
            long id,
            String entryType,
            LocalDate entryDate,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime deletedAt,
            String energy,
            String mood,
            String body
    ) {}

    public JournalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public EntryResponse insert(String body, String entryType, LocalDate entryDate, String energy, String mood, float[] embedding) {
        String vectorLiteral = toVectorLiteral(embedding);

        EntryResponse entry = jdbc.queryForObject("""
                INSERT INTO journal_entry (body, entry_type, entry_date, energy, mood)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id, entry_type, entry_date, created_at, updated_at, energy, mood, body
                """, (rs, row) -> mapActive(rs), body, entryType, entryDate, energy, mood);

        if (entry == null) {
            throw new IllegalStateException("Failed to insert journal entry");
        }

        jdbc.update("""
                INSERT INTO entry_embedding (entry_id, embedding)
                VALUES (?, CAST(? AS vector))
                """, entry.id(), vectorLiteral);

        return entry;
    }

    public Optional<EntryResponse> findById(long id) {
        List<EntryResponse> list = jdbc.query("""
                SELECT id, entry_type, entry_date, created_at, updated_at, energy, mood, body
                FROM journal_entry
                WHERE id = ? AND deleted_at IS NULL
                """, (rs, row) -> mapActive(rs), id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<FullEntry> findByIdIncludeDeleted(long id) {
        List<FullEntry> list = jdbc.query("""
                SELECT id, entry_type, entry_date, created_at, updated_at, deleted_at, energy, mood, body
                FROM journal_entry
                WHERE id = ?
                """, (rs, row) -> mapFull(rs), id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public boolean hasDailyEntryForDate(LocalDate date) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM journal_entry
                WHERE entry_type = 'DAILY_PROMPT' AND entry_date = ? AND deleted_at IS NULL
                """, Integer.class, date);
        return count != null && count > 0;
    }

    public Optional<Long> findConflictingDailyEntryId(LocalDate date) {
        List<Long> list = jdbc.query("""
                SELECT id FROM journal_entry
                WHERE entry_type = 'DAILY_PROMPT' AND entry_date = ? AND deleted_at IS NULL
                LIMIT 1
                """, (rs, row) -> rs.getLong("id"), date);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<EntryResponse> findAll() {
        return jdbc.query("""
                SELECT id, entry_type, entry_date, created_at, updated_at, energy, mood, body
                FROM journal_entry
                WHERE deleted_at IS NULL
                ORDER BY entry_date DESC, created_at DESC, id DESC
                """, (rs, row) -> mapActive(rs));
    }

    public EntryResponse update(long id, String body, String energy, String mood, float[] embedding) {
        String vectorLiteral = toVectorLiteral(embedding);

        EntryResponse updated = jdbc.queryForObject("""
                UPDATE journal_entry
                SET body = ?, energy = ?, mood = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND deleted_at IS NULL
                RETURNING id, entry_type, entry_date, created_at, updated_at, energy, mood, body
                """, (rs, row) -> mapActive(rs), body, energy, mood, id);

        if (updated == null) {
            throw new IllegalStateException("Failed to update journal entry: " + id);
        }

        int rows = jdbc.update("""
                UPDATE entry_embedding
                SET embedding = CAST(? AS vector), created_at = CURRENT_TIMESTAMP
                WHERE entry_id = ?
                """, vectorLiteral, id);

        if (rows == 0) {
            jdbc.update("""
                    INSERT INTO entry_embedding (entry_id, embedding)
                    VALUES (?, CAST(? AS vector))
                    """, id, vectorLiteral);
        }

        return updated;
    }

    public boolean softDelete(long id) {
        int rows = jdbc.update("""
                UPDATE journal_entry
                SET deleted_at = CURRENT_TIMESTAMP
                WHERE id = ? AND deleted_at IS NULL
                """, id);
        return rows > 0;
    }

    public List<FullEntry> findTrash() {
        return jdbc.query("""
                SELECT id, entry_type, entry_date, created_at, updated_at, deleted_at, energy, mood, body
                FROM journal_entry
                WHERE deleted_at IS NOT NULL
                ORDER BY deleted_at DESC, id DESC
                """, (rs, row) -> mapFull(rs));
    }

    public EntryResponse restore(long id) {
        return jdbc.queryForObject("""
                UPDATE journal_entry
                SET deleted_at = NULL
                WHERE id = ? AND deleted_at IS NOT NULL
                RETURNING id, entry_type, entry_date, created_at, updated_at, energy, mood, body
                """, (rs, row) -> mapActive(rs), id);
    }

    public boolean permanentDelete(long id) {
        int rows = jdbc.update("""
                DELETE FROM journal_entry
                WHERE id = ? AND deleted_at IS NOT NULL
                """, id);
        return rows > 0;
    }

    public int emptyTrash() {
        return jdbc.update("""
                DELETE FROM journal_entry
                WHERE deleted_at IS NOT NULL
                """);
    }

    public int purgeOldTrash(int retentionDays) {
        return jdbc.update("""
                DELETE FROM journal_entry
                WHERE deleted_at IS NOT NULL
                  AND deleted_at < CURRENT_TIMESTAMP - (CAST(? AS text) || ' days')::interval
                """, retentionDays);
    }

    public int countNonDeletedBefore(LocalDate beforeDate) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM journal_entry
                WHERE deleted_at IS NULL AND entry_date < ?
                """, Integer.class, beforeDate);
        return count != null ? count : 0;
    }

    public List<EntryResponse> findRecentBefore(LocalDate beforeDate, int limit) {
        return jdbc.query("""
                SELECT id, entry_type, entry_date, created_at, updated_at, energy, mood, body
                FROM journal_entry
                WHERE deleted_at IS NULL AND entry_date < ?
                ORDER BY entry_date DESC, created_at DESC, id DESC
                LIMIT ?
                """, (rs, row) -> mapActive(rs), beforeDate, limit);
    }

    public List<EntryResponse> findByDate(LocalDate date) {
        return jdbc.query("""
                SELECT id, entry_type, entry_date, created_at, updated_at, energy, mood, body
                FROM journal_entry
                WHERE deleted_at IS NULL AND entry_date = ?
                ORDER BY created_at ASC, id ASC
                """, (rs, row) -> mapActive(rs), date);
    }

    public List<EntryResponse> findRecentLowMoodOrEnergy(int limit) {
        return jdbc.query("""
                SELECT id, entry_type, entry_date, created_at, updated_at, energy, mood, body
                FROM journal_entry
                WHERE deleted_at IS NULL AND (mood = 'BAD' OR (energy = 'LOW' AND (body ILIKE '%drain%' OR body ILIKE '%exhaust%' OR body ILIKE '%fatigue%' OR body ILIKE '%slump%' OR body ILIKE '%insomnia%' OR body ILIKE '%struggl%')))
                ORDER BY entry_date DESC, created_at DESC, id DESC
                LIMIT ?
                """, (rs, row) -> mapActive(rs), limit);
    }

    public List<EntryResponse> findRecentRechargeMoments(int limit) {
        return jdbc.query("""
                SELECT id, entry_type, entry_date, created_at, updated_at, energy, mood, body
                FROM journal_entry
                WHERE deleted_at IS NULL AND (body ILIKE '%walk%' OR body ILIKE '%chai%' OR body ILIKE '%tea%' OR body ILIKE '%cycle%' OR body ILIKE '%cycling%' OR body ILIKE '%rohan%' OR body ILIKE '%lake%')
                ORDER BY entry_date DESC, created_at DESC, id DESC
                LIMIT ?
                """, (rs, row) -> mapActive(rs), limit);
    }

    public List<EntryResponse> findInDateRange(LocalDate fromInclusive, LocalDate toExclusive) {
        return jdbc.query("""
                SELECT id, entry_type, entry_date, created_at, updated_at, energy, mood, body
                FROM journal_entry
                WHERE deleted_at IS NULL AND entry_date >= ? AND entry_date < ?
                ORDER BY entry_date DESC, created_at DESC, id DESC
                """, (rs, row) -> mapActive(rs), fromInclusive, toExclusive);
    }

    public List<EntryResponse> findSimilarBefore(float[] embedding, LocalDate beforeDate, List<Long> excludeIds, int limit) {
        String vectorLiteral = toVectorLiteral(embedding);
        if (excludeIds == null || excludeIds.isEmpty()) {
            return jdbc.query("""
                    SELECT e.id, e.entry_type, e.entry_date, e.created_at, e.updated_at, e.energy, e.mood, e.body
                    FROM entry_embedding emb
                    JOIN journal_entry e ON emb.entry_id = e.id
                    WHERE e.deleted_at IS NULL AND e.entry_date < ?
                    ORDER BY emb.embedding <=> CAST(? AS vector)
                    LIMIT ?
                    """, (rs, row) -> mapActive(rs), beforeDate, vectorLiteral, limit);
        } else {
            String placeholders = String.join(",", java.util.Collections.nCopies(excludeIds.size(), "?"));
            List<Object> params = new java.util.ArrayList<>();
            params.add(beforeDate);
            params.addAll(excludeIds);
            params.add(vectorLiteral);
            params.add(limit);
            String sql = """
                    SELECT e.id, e.entry_type, e.entry_date, e.created_at, e.updated_at, e.energy, e.mood, e.body
                    FROM entry_embedding emb
                    JOIN journal_entry e ON emb.entry_id = e.id
                    WHERE e.deleted_at IS NULL AND e.entry_date < ? AND e.id NOT IN (%s)
                    ORDER BY emb.embedding <=> CAST(? AS vector)
                    LIMIT ?
                    """.formatted(placeholders);
            return jdbc.query(sql, (rs, row) -> mapActive(rs), params.toArray());
        }
    }

    public List<EntryResponse> findSimilar(float[] embedding, int limit) {
        return jdbc.query("""
                SELECT e.id, e.entry_type, e.entry_date, e.created_at, e.updated_at, e.energy, e.mood, e.body
                FROM entry_embedding emb
                JOIN journal_entry e ON emb.entry_id = e.id
                WHERE e.deleted_at IS NULL
                ORDER BY emb.embedding <=> CAST(? AS vector)
                LIMIT ?
                """, (rs, row) -> mapActive(rs), toVectorLiteral(embedding), limit);
    }

    private static String toVectorLiteral(float[] vector) {
        if (vector == null || vector.length != 768) {
            throw new IllegalArgumentException("Expected a 768-dimensional nomic-embed-text vector");
        }
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) out.append(',');
            out.append(Float.toString(vector[i]));
        }
        return out.append(']').toString();
    }

    private static EntryResponse mapActive(ResultSet rs) throws SQLException {
        return new EntryResponse(
                rs.getLong("id"),
                rs.getString("entry_type"),
                rs.getObject("entry_date", LocalDate.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getString("energy"),
                rs.getString("mood"),
                rs.getString("body")
        );
    }

    private static FullEntry mapFull(ResultSet rs) throws SQLException {
        return new FullEntry(
                rs.getLong("id"),
                rs.getString("entry_type"),
                rs.getObject("entry_date", LocalDate.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("deleted_at", OffsetDateTime.class),
                rs.getString("energy"),
                rs.getString("mood"),
                rs.getString("body")
        );
    }
}
