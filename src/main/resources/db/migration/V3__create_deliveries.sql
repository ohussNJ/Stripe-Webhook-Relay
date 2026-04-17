CREATE TABLE deliveries (
    id                BIGSERIAL PRIMARY KEY,
    event_id          BIGINT      NOT NULL REFERENCES events(id),
    endpoint_id       BIGINT      NOT NULL REFERENCES endpoints(id),
    status            TEXT        NOT NULL DEFAULT 'pending'
                          CHECK (status IN ('pending', 'in_progress', 'delivered', 'dead_lettered')),
    attempts          INT         NOT NULL DEFAULT 0,
    next_retry_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_attempted_at TIMESTAMPTZ
);

CREATE INDEX idx_deliveries_status_retry ON deliveries (status, next_retry_at)
    WHERE status = 'pending';
