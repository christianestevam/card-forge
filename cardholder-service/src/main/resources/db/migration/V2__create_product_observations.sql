-- Última observação de cada produto conhecida pelo cadastro (ADR-002 do Desenho de Domínio):
-- gravada no cadastro e a cada consulta bem-sucedida ao catálogo; usada pela consulta
-- consolidada, sinalizada como STALE, quando o catálogo não responde.
CREATE TABLE product_observations (
    product_id  UUID          PRIMARY KEY,
    name        VARCHAR(120),
    bin         VARCHAR(8)    NOT NULL,
    status      VARCHAR(20)   NOT NULL CHECK (status IN ('ACTIVE', 'CANCELED')),
    observed_at TIMESTAMPTZ   NOT NULL
);
