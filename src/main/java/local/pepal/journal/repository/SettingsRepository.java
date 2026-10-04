package local.pepal.journal.repository;

import local.pepal.journal.api.JournalDtos.SettingsRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class SettingsRepository {
    private final JdbcTemplate jdbc;

    public SettingsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SettingsRecord> get() {
        List<SettingsRecord> list = jdbc.query("""
                SELECT id, persona_preset, custom_persona, language, user_name, updated_at
                FROM settings
                WHERE id = 1
                """, (rs, rowNum) -> map(rs));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public SettingsRecord update(String preset, String customPersona, String language, String userName) {
        return jdbc.queryForObject("""
                UPDATE settings
                SET persona_preset = ?, custom_persona = ?, language = ?, user_name = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = 1
                RETURNING id, persona_preset, custom_persona, language, user_name, updated_at
                """, (rs, rowNum) -> map(rs), preset, customPersona, language, userName);
    }

    private static SettingsRecord map(ResultSet rs) throws SQLException {
        return new SettingsRecord(
                rs.getShort("id"),
                rs.getString("persona_preset"),
                rs.getString("custom_persona"),
                rs.getString("language"),
                rs.getString("user_name"),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
