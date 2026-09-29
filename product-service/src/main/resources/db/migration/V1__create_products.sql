CREATE TABLE products (
    id          UUID          PRIMARY KEY,
    name        VARCHAR(120)  NOT NULL,
    description VARCHAR(500),
    bin         VARCHAR(8)    NOT NULL,
    status      VARCHAR(20)   NOT NULL CHECK (status IN ('ACTIVE', 'CANCELED')),
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    version     BIGINT        NOT NULL,
    CONSTRAINT uk_products_bin UNIQUE (bin)
);
