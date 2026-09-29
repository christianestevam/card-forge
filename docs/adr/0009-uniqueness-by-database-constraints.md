# ADR-0009: Unicidade garantida por constraints e índices parciais

- **Status:** Accepted (2026-09-27)
- **Contexto:** verificações prévias na aplicação não resistem à concorrência.
- **Decisão:** as invariantes ficam no banco: `products(bin)`, `cardholders(cpf)` (inclusive cancelados), `cards(pan_hmac)`, `cards(issuance_request_id)`, `issuance_processing(issuance_request_id)` e o índice parcial `cards(cardholder_id, product_id) WHERE status <> 'CANCELED'`. Os conflitos esperados são tratados pelo nome da constraint específica, nunca de forma genérica.
- **Consequências:** a aplicação pode fazer checagens rápidas, mas a garantia vem do banco. Cancelar um cartão libera o índice parcial para um novo cartão.
- **Alternativas rejeitadas:** checagem com `SELECT` antes do `INSERT`, com condição de corrida; lock de aplicação, que não protege contra outras instâncias.
