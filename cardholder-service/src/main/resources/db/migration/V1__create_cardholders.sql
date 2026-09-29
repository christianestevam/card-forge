CREATE TABLE cardholders (
    id          UUID          PRIMARY KEY,
    cpf         VARCHAR(11)   NOT NULL,
    full_name   VARCHAR(120)  NOT NULL,
    birth_date  DATE          NOT NULL,
    product_id  UUID          NOT NULL,
    status      VARCHAR(20)   NOT NULL CHECK (status IN ('ACTIVE', 'BLOCKED', 'CANCELED')),
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    version     BIGINT        NOT NULL,
    -- CPF único em toda a base, inclusive contra portadores cancelados (BR2.2).
    CONSTRAINT uk_cardholders_cpf UNIQUE (cpf)
);

CREATE TABLE issuance_requests (
    id             UUID         PRIMARY KEY,
    cardholder_id  UUID         NOT NULL REFERENCES cardholders (id),
    product_id     UUID         NOT NULL,
    status         VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'ISSUED', 'FAILED')),
    failure_reason VARCHAR(50),
    card_id        UUID,
    requested_at   TIMESTAMPTZ  NOT NULL,
    decided_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL
);

CREATE INDEX ix_issuance_requests_cardholder ON issuance_requests (cardholder_id);
CREATE INDEX ix_issuance_requests_pending ON issuance_requests (requested_at) WHERE status = 'PENDING';
