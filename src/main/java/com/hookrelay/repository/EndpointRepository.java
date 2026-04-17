package com.hookrelay.repository;

import com.hookrelay.model.Endpoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class EndpointRepository {

    private final JdbcTemplate jdbc;

    public EndpointRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Endpoint save(String url, List<String> eventTypes) {
        var keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            var ps = conn.prepareStatement(
                    "INSERT INTO endpoints (url, event_types) VALUES (?, ?) RETURNING id",
                    new String[]{"id"}
            );
            ps.setString(1, url);
            ps.setArray(2, conn.createArrayOf("text", eventTypes.toArray()));
            return ps;
        }, keyHolder);

        long id = keyHolder.getKey().longValue();
        return findById(id).orElseThrow();
    }

    public Optional<Endpoint> findById(long id) {
        var results = jdbc.query(
                "SELECT * FROM endpoints WHERE id = ?",
                ENDPOINT_ROW_MAPPER,
                id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<Endpoint> findAll() {
        return jdbc.query("SELECT * FROM endpoints ORDER BY created_at DESC", ENDPOINT_ROW_MAPPER);
    }

    // Returns endpoints where event_types array is empty (wildcard) or contains the given type
    public List<Endpoint> findMatchingEndpoints(String eventType) {
        return jdbc.query(
                "SELECT * FROM endpoints WHERE event_types = '{}' OR ? = ANY(event_types)",
                ENDPOINT_ROW_MAPPER,
                eventType
        );
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM endpoints WHERE id = ?", id);
    }

    private static final RowMapper<Endpoint> ENDPOINT_ROW_MAPPER = (rs, rowNum) -> new Endpoint(
            rs.getLong("id"),
            rs.getString("url"),
            toStringList(rs.getArray("event_types")),
            rs.getTimestamp("created_at").toInstant()
    );

    private static List<String> toStringList(Array array) throws SQLException {
        if (array == null) return Collections.emptyList();
        return Arrays.asList((String[]) array.getArray());
    }
}
