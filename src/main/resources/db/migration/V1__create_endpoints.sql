CREATE TABLE endpoints (
    id          BIGSERIAL PRIMARY KEY,
    url         TEXT NOT NULL,
    event_types TEXT[] NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
