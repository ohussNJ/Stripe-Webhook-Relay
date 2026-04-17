CREATE TABLE events (
    id              BIGSERIAL PRIMARY KEY,
    stripe_event_id TEXT        NOT NULL UNIQUE,
    type            TEXT        NOT NULL,
    payload         JSONB       NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_stripe_event_id ON events (stripe_event_id);
