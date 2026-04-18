package com.hookrelay.repository;

import com.hookrelay.model.DeliveryAttempt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DeliveryAttemptRepository {

    private final JdbcTemplate jdbc;

    public DeliveryAttemptRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(long deliveryId, Integer httpStatus, long latencyMs, String outcome) {
        jdbc.update(
                "INSERT INTO delivery_attempts (delivery_id, http_status, latency_ms, outcome) VALUES (?, ?, ?, ?)",
                deliveryId, httpStatus, latencyMs, outcome
        );
    }

    public List<DeliveryAttempt> findByDeliveryId(long deliveryId) {
        return jdbc.query(
                "SELECT * FROM delivery_attempts WHERE delivery_id = ? ORDER BY attempted_at",
                ATTEMPT_ROW_MAPPER,
                deliveryId
        );
    }

    private static final RowMapper<DeliveryAttempt> ATTEMPT_ROW_MAPPER = (rs, rowNum) -> new DeliveryAttempt(
            rs.getLong("id"),
            rs.getLong("delivery_id"),
            rs.getTimestamp("attempted_at").toInstant(),
            rs.getObject("http_status", Integer.class),
            rs.getLong("latency_ms"),
            rs.getString("outcome")
    );
}
