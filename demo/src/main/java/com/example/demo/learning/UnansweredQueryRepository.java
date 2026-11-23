package com.example.demo.learning;

import com.example.demo.model.UnansweredQuery;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Stores questions the book could not answer. Status lifecycle:
 *   PENDING  -> inserted from the unanswered-queries topic
 *   GENERATED-> an LLM answer was produced and published to learned-answers
 *   STORED   -> that answer was embedded and saved to the vector store
 * The table is created on startup if missing (see schema.sql for the canonical DDL).
 */
@Repository
public class UnansweredQueryRepository {

    private static final Logger log = LoggerFactory.getLogger(UnansweredQueryRepository.class);

    public static final String PENDING = "PENDING";
    public static final String GENERATED = "GENERATED";
    public static final String STORED = "STORED";

    private final JdbcTemplate jdbcTemplate;

    public UnansweredQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS unanswered_queries (
                    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    question        TEXT        NOT NULL,
                    status          TEXT        NOT NULL DEFAULT 'PENDING',
                    answer          TEXT,
                    source_event_id TEXT,
                    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """);
        log.info("unanswered_queries table ready");
    }

    /** Insert a PENDING row unless an active (PENDING/GENERATED) one exists for the same question. */
    public int insertIfAbsent(String question, String sourceEventId) {
        return jdbcTemplate.update("""
                INSERT INTO unanswered_queries (question, status, source_event_id)
                SELECT ?, 'PENDING', ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM unanswered_queries
                    WHERE question = ? AND status IN ('PENDING', 'GENERATED')
                )
                """, question, sourceEventId, question);
    }

    public Optional<UnansweredQuery> findById(long id) {
        List<UnansweredQuery> rows = jdbcTemplate.query("""
                SELECT id, question, status, answer, source_event_id,
                       to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SSOF') AS created_at
                FROM unanswered_queries WHERE id = ?
                """, this::map, id);
        return rows.stream().findFirst();
    }

    public List<UnansweredQuery> list(String status, int limit) {
        if (status == null || status.isBlank()) {
            return jdbcTemplate.query("""
                    SELECT id, question, status, answer, source_event_id,
                           to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SSOF') AS created_at
                    FROM unanswered_queries ORDER BY created_at DESC LIMIT ?
                    """, this::map, limit);
        }
        return jdbcTemplate.query("""
                SELECT id, question, status, answer, source_event_id,
                       to_char(created_at, 'YYYY-MM-DD"T"HH24:MI:SSOF') AS created_at
                FROM unanswered_queries WHERE status = ? ORDER BY created_at DESC LIMIT ?
                """, this::map, status, limit);
    }

    public void markGenerated(long id, String answer, String eventId) {
        jdbcTemplate.update("""
                UPDATE unanswered_queries
                SET status = 'GENERATED', answer = ?, source_event_id = ?, updated_at = now()
                WHERE id = ?
                """, answer, eventId, id);
    }

    public void markStored(long id) {
        jdbcTemplate.update(
                "UPDATE unanswered_queries SET status = 'STORED', updated_at = now() WHERE id = ?", id);
    }

    private UnansweredQuery map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new UnansweredQuery(
                rs.getLong("id"),
                rs.getString("question"),
                rs.getString("status"),
                rs.getString("answer"),
                rs.getString("source_event_id"),
                rs.getString("created_at"));
    }
}
