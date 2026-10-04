package local.pepal.journal.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import local.pepal.journal.api.JournalDtos.DailyPromptRecord;
import local.pepal.journal.api.JournalDtos.PromptQuestionDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class DailyPromptRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public DailyPromptRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Optional<DailyPromptRecord> findForDate(LocalDate date) {
        List<DailyPromptRecord> list = jdbc.query("""
                SELECT prompt_date, questions, source, reshuffle_count, created_at
                FROM daily_prompt
                WHERE prompt_date = ?
                """, (rs, rowNum) -> mapRecord(rs), date);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public boolean insert(DailyPromptRecord record) {
        String json = serializeQuestions(record.questions());
        int rows = jdbc.update("""
                INSERT INTO daily_prompt (prompt_date, questions, source, reshuffle_count, created_at)
                VALUES (?, ?::jsonb, ?, ?, ?)
                ON CONFLICT (prompt_date) DO NOTHING
                """, record.promptDate(), json, record.source(), record.reshuffleCount(),
                record.createdAt() != null ? record.createdAt() : OffsetDateTime.now());
        return rows > 0;
    }

    public void updateQuestionsAndSource(LocalDate date, List<PromptQuestionDto> questions, String source, short reshuffleCount) {
        String json = serializeQuestions(questions);
        jdbc.update("""
                UPDATE daily_prompt
                SET questions = ?::jsonb, source = ?, reshuffle_count = ?
                WHERE prompt_date = ?
                """, json, source, reshuffleCount, date);
    }

    public List<PromptQuestionDto> findRecentQuestions(LocalDate beforeDate, int days) {
        LocalDate startDate = beforeDate.minusDays(days);
        List<String> jsonList = jdbc.query("""
                SELECT questions
                FROM daily_prompt
                WHERE prompt_date >= ? AND prompt_date < ?
                ORDER BY prompt_date DESC
                """, (rs, rowNum) -> rs.getString("questions"), startDate, beforeDate);

        List<PromptQuestionDto> results = new ArrayList<>();
        for (String json : jsonList) {
            results.addAll(deserializeQuestions(json));
        }
        return results;
    }

    private String serializeQuestions(List<PromptQuestionDto> questions) {
        try {
            return objectMapper.writeValueAsString(questions != null ? questions : List.of());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize prompt questions to JSON", e);
        }
    }

    private List<PromptQuestionDto> deserializeQuestions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<PromptQuestionDto>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize prompt questions from JSON", e);
        }
    }

    private DailyPromptRecord mapRecord(ResultSet rs) throws SQLException {
        return new DailyPromptRecord(
                rs.getObject("prompt_date", LocalDate.class),
                deserializeQuestions(rs.getString("questions")),
                rs.getString("source"),
                rs.getShort("reshuffle_count"),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }
}
