package com.hookrelay.repository;

import com.hookrelay.model.Event;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class EventRepository {

    private final JdbcTemplate jdbc;

    public EventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Return saved event or empty if stripe_event_id already exists (duplicate delivery from Stripe)
    public Optional<Event> save(String stripeEventId, String type, String payload) {
        try {
            var keyHolder = new GeneratedKeyHolder();
            jdbc.update(conn -> {
                var ps = conn.prepareStatement(
                        "INSERT INTO events (stripe_event_id, type, payload) VALUES (?, ?, ?::jsonb)",
                        new String[]{"id"}
                );
                ps.setString(1, stripeEventId);
                ps.setString(2, type);
                ps.setString(3, payload);
                return ps;
            }, keyHolder);

            long id = keyHolder.getKey().longValue();
            return findById(id);
        } catch (DuplicateKeyException e) {
            return Optional.empty();
        }
    }

    public Optional<Event> findById(long id) {
        var results = jdbc.query(
                "SELECT * FROM events WHERE id = ?",
                EVENT_ROW_MAPPER,
                id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    private static final RowMapper<Event> EVENT_ROW_MAPPER = (rs, rowNum) -> new Event(
            rs.getLong("id"),
            rs.getString("stripe_event_id"),
            rs.getString("type"),
            rs.getString("payload"),
            rs.getTimestamp("received_at").toInstant()
    );
}
