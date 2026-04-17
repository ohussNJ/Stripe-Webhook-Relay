package com.hookrelay.repository;

import com.hookrelay.model.Delivery;
import com.hookrelay.model.DeliveryStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class DeliveryRepository {

    private final JdbcTemplate jdbc;

    public DeliveryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Delivery save(long eventId, long endpointId) {
        var keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            var ps = conn.prepareStatement(
                    "INSERT INTO deliveries (event_id, endpoint_id) VALUES (?, ?)",
                    new String[]{"id"}
            );
            ps.setLong(1, eventId);
            ps.setLong(2, endpointId);
            return ps;
        }, keyHolder);

        long id = keyHolder.getKey().longValue();
        return findById(id).orElseThrow();
    }

    // Claims the next pending delivery atomically. Uses SKIP LOCKED so multiple workers
    // can poll concurrently without contention. Runs in its own transaction so the lock
    // is released before the caller makes the outbound HTTP call.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Delivery> claimNextPending() {
        var results = jdbc.query(
                """
                UPDATE deliveries
                SET status = 'in_progress', last_attempted_at = now()
                WHERE id = (
                    SELECT id FROM deliveries
                    WHERE status = 'pending' AND next_retry_at <= now()
                    ORDER BY next_retry_at
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED
                )
                RETURNING *
                """,
                DELIVERY_ROW_MAPPER
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public void markDelivered(long id) {
        jdbc.update(
                "UPDATE deliveries SET status = 'delivered', last_attempted_at = now(), attempts = attempts + 1 WHERE id = ?",
                id
        );
    }

    public void markForRetry(long id, Instant nextRetryAt) {
        jdbc.update(
                "UPDATE deliveries SET status = 'pending', attempts = attempts + 1, next_retry_at = ?, last_attempted_at = now() WHERE id = ?",
                Timestamp.from(nextRetryAt),
                id
        );
    }

    public void markDeadLettered(long id) {
        jdbc.update(
                "UPDATE deliveries SET status = 'dead_lettered', attempts = attempts + 1, last_attempted_at = now() WHERE id = ?",
                id
        );
    }

    public Optional<Delivery> findById(long id) {
        var results = jdbc.query("SELECT * FROM deliveries WHERE id = ?", DELIVERY_ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<Delivery> findByEventId(long eventId) {
        return jdbc.query(
                "SELECT * FROM deliveries WHERE event_id = ? ORDER BY id",
                DELIVERY_ROW_MAPPER,
                eventId
        );
    }

    public int resetToPending(long id) {
        return jdbc.update(
                "UPDATE deliveries SET status = 'pending', next_retry_at = now() WHERE id = ? AND status = 'dead_lettered'",
                id
        );
    }

    public int resetAllToPending(long eventId) {
        return jdbc.update(
                "UPDATE deliveries SET status = 'pending', next_retry_at = now() WHERE event_id = ? AND status = 'dead_lettered'",
                eventId
        );
    }

    private static final RowMapper<Delivery> DELIVERY_ROW_MAPPER = (rs, rowNum) -> {
        var lastAttempted = rs.getTimestamp("last_attempted_at");
        return new Delivery(
                rs.getLong("id"),
                rs.getLong("event_id"),
                rs.getLong("endpoint_id"),
                DeliveryStatus.valueOf(rs.getString("status").toUpperCase()),
                rs.getInt("attempts"),
                rs.getTimestamp("next_retry_at").toInstant(),
                lastAttempted != null ? lastAttempted.toInstant() : null
        );
    };
}
