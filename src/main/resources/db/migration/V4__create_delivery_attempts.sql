CREATE TABLE delivery_attempts (
    id           BIGSERIAL PRIMARY KEY,
    delivery_id  BIGINT      NOT NULL REFERENCES deliveries(id),
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    http_status  INT,
    latency_ms   BIGINT      NOT NULL,
    outcome      TEXT        NOT NULL
                     CHECK (outcome IN ('succeeded', 'retrying', 'dead_lettered'))
);

CREATE INDEX idx_delivery_attempts_delivery_id ON delivery_attempts (delivery_id);
