-- Outbox transacional (cardforge-platform). Gravado na mesma transação do dado de negócio.
CREATE TABLE outbox_events (
    event_id       UUID         PRIMARY KEY,
    aggregate_id   UUID         NOT NULL,
    destination    VARCHAR(80)  NOT NULL,
    event_type     VARCHAR(80)  NOT NULL,
    event_version  INT          NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    correlation_id VARCHAR(64)  NOT NULL,
    payload        JSONB        NOT NULL,
    sent_at        TIMESTAMPTZ
);

CREATE INDEX ix_outbox_events_pending ON outbox_events (occurred_at) WHERE sent_at IS NULL;
