-- BIN do cartão (8 primeiros dígitos do PAN, públicos no produto), para medir a ocupação da faixa
-- de cada BIN. Nulo em cartões emitidos antes desta migração (dados locais descartáveis).
ALTER TABLE cards ADD COLUMN bin VARCHAR(8);
CREATE INDEX ix_cards_bin ON cards (bin);
