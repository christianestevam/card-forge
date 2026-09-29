-- Cartões: só o HMAC do PAN e os 4 últimos dígitos são guardados; nunca o PAN completo nem CVV.
CREATE TABLE cards (
    id                  UUID         PRIMARY KEY,
    cardholder_id       UUID         NOT NULL,
    product_id          UUID         NOT NULL,
    issuance_request_id UUID         NOT NULL,
    pan_hmac            VARCHAR(64)  NOT NULL,
    pan_last_four       VARCHAR(4)   NOT NULL,
    expiration_date     VARCHAR(7)   NOT NULL,
    status              VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'BLOCKED', 'CANCELED')),
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    version             BIGINT       NOT NULL,
    CONSTRAINT uk_cards_pan_hmac UNIQUE (pan_hmac),
    CONSTRAINT uk_cards_issuance_request UNIQUE (issuance_request_id)
);

-- No máximo um cartão não cancelado por portador e produto (BR4.3).
CREATE UNIQUE INDEX uk_cards_active_per_cardholder_product
    ON cards (cardholder_id, product_id) WHERE status <> 'CANCELED';

-- Desfecho terminal de cada solicitação (BR4.2): gravado uma vez, nunca reavaliado.
CREATE TABLE issuance_processing (
    issuance_request_id UUID         PRIMARY KEY,
    status              VARCHAR(20)  NOT NULL,
    card_id             UUID,
    failure_reason      VARCHAR(50),
    processed_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_issuance_processing_outcome CHECK (
        (status = 'ISSUED' AND card_id IS NOT NULL AND failure_reason IS NULL)
        OR (status = 'FAILED' AND card_id IS NULL AND failure_reason IS NOT NULL))
);
