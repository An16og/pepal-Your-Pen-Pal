package local.pepal.journal.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import local.pepal.journal.api.JournalDtos.SummaryResponse;
import local.pepal.journal.api.JournalDtos.SummaryStats;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class SummaryRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public SummaryRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<SummaryResponse> findByType(String periodType, int limit, int offset) {
        return jdbc.query("""
                SELECT id, period_type, period_start, period_end, headline, body, themes, stats,
                       source, is_final, stale, created_at, updated_at
                FROM journal_summary
                WHERE period_type = ?
                ORDER BY period_start DESC, id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> mapRow(rs), periodType, limit, offset);
    }

    public Optional<SummaryResponse> findByTypeAndStart(String periodType, LocalDate periodStart) {
        List<SummaryResponse> list = jdbc.query("""
                SELECT id, period_type, period_start, period_end, headline, body, themes, stats,
                       source, is_final, stale, created_at, updated_at
                FROM journal_summary
                WHERE period_type = ? AND period_start = ?
                """, (rs, rowNum) -> mapRow(rs), periodType, periodStart);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Optional<SummaryResponse> findById(long id) {
        List<SummaryResponse> list = jdbc.query("""
                SELECT id, period_type, period_start, period_end, headline, body, themes, stats,
                       source, is_final, stale, created_at, updated_at
                FROM journal_summary
                WHERE id = ?
                """, (rs, rowNum) -> mapRow(rs), id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public SummaryResponse upsert(
            String periodType,
            LocalDate periodStart,
            LocalDate periodEnd,
            String headline,
            String body,
            List<String> themes,
            SummaryStats stats,
            String source,
            boolean isFinal,
            boolean stale
    ) {
        String statsJson;
        try {
            statsJson = objectMapper.writeValueAsString(stats);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize summary stats", e);
        }

        String[] themesArray = themes != null ? themes.toArray(new String[0]) : new String[0];

        return jdbc.execute((java.sql.Connection conn) -> {
            Array sqlThemes = conn.createArrayOf("text", themesArray);
            try (var ps = conn.prepareStatement("""
                    INSERT INTO journal_summary (period_type, period_start, period_end, headline, body, themes, stats, source, is_final, stale, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (period_type, period_start)
                    DO UPDATE SET
                        period_end = EXCLUDED.period_end,
                        headline = EXCLUDED.headline,
                        body = EXCLUDED.body,
                        themes = EXCLUDED.themes,
                        stats = EXCLUDED.stats,
                        source = EXCLUDED.source,
                        is_final = EXCLUDED.is_final,
                        stale = EXCLUDED.stale,
                        updated_at = CURRENT_TIMESTAMP
                    RETURNING id, period_type, period_start, period_end, headline, body, themes, stats,
                              source, is_final, stale, created_at, updated_at
                    """)) {
                ps.setString(1, periodType);
                ps.setObject(2, periodStart);
                ps.setObject(3, periodEnd);
                ps.setString(4, headline);
                ps.setString(5, body);
                ps.setArray(6, sqlThemes);
                ps.setString(7, statsJson);
                ps.setString(8, source);
                ps.setBoolean(9, isFinal);
                ps.setBoolean(10, stale);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                    throw new IllegalStateException("Failed to upsert summary");
                }
            }
        });
    }

    private SummaryResponse mapRow(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String periodType = rs.getString("period_type");
        LocalDate periodStart = rs.getObject("period_start", LocalDate.class);
        LocalDate periodEnd = rs.getObject("period_end", LocalDate.class);
        String headline = rs.getString("headline");
        String body = rs.getString("body");

        List<String> themes = new ArrayList<>();
        Array themesArray = rs.getArray("themes");
        if (themesArray != null) {
            String[] arr = (String[]) themesArray.getArray();
            if (arr != null) {
                themes.addAll(Arrays.asList(arr));
            }
        }

        String statsJson = rs.getString("stats");
        SummaryStats stats;
        try {
            stats = objectMapper.readValue(statsJson, SummaryStats.class);
        } catch (Exception e) {
            stats = new SummaryStats(0, 0, 0, 0, 0, 0, 0);
        }

        String source = rs.getString("source");
        boolean isFinal = rs.getBoolean("is_final");
        boolean stale = rs.getBoolean("stale");
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        OffsetDateTime updatedAt = rs.getObject("updated_at", OffsetDateTime.class);

        return new SummaryResponse(
                id,
                periodType,
                periodStart,
                periodEnd,
                headline,
                body,
                themes,
                stats,
                source,
                isFinal,
                stale,
                createdAt,
                updatedAt
        );
    }
}
