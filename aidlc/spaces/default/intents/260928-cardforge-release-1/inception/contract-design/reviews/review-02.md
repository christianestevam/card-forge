## Review

**Verdict:** READY
**Reviewer:** aidlc-architecture-reviewer-agent
**Date:** 2026-09-29T03:56:43Z
**Iteration:** 1

### Findings

| ID | Severity | Location | Finding | Required action | Status |
|---|---|---|---|---|---|
| R-01 | Minor | `contract-summary.md` C1–C3 OpenAPI blocks | Nenhum endpoint declarava 401/403 ou `WWW-Authenticate`; só constava na prosa. Verificado: as três specs (C1 product-service, C2 cardholder-service, C3 card-service) agora declaram `components.responses.Unauthorized` (com o header `WWW-Authenticate`, descrição RFC 6750) e `components.responses.Forbidden`, e todos os 11 endpoints/pathItems (`POST/GET/PATCH /products`, `/products/{id}/cancel`, `POST /cardholders`, `GET /cardholders/{id}`, `StatusAction` de portador, `GET /overview`, `GET /cards/{id}`, `GET /cards`, `StatusAction` de cartão) referenciam `"401": {$ref:...Unauthorized}` / `"403": {$ref:...Forbidden}` (confirmado por grep: 11 pares, todos via `$ref`, nenhum inline). A tabela de headers (linha 74) inclui `WWW-Authenticate`. | Nenhuma — corrigido. | Resolved |
| R-02 | Minor | `$ref: "cardforge-platform#/..."` | Não havia convenção declarada de como a referência de esquema compartilhado é resolvida na implementação. Verificado: a seção "Regras de propriedade e de mudança (Q8)" (linha 567) agora registra a convenção pedida — OpenAPI publicado por springdoc a partir do código; `ProblemDetail`, `EventEnvelope` e `PageMetadata` são classes do `cardforge-platform` expostas no OpenAPI de cada serviço a partir da mesma fonte, sem YAML compartilhado; o `contract-summary.md` é a referência de desenho que o código segue; o AsyncAPI de C4/C5 fica junto ao código dos serviços donos. Coerente com Q10 e com a resposta consolidada. | Nenhuma — corrigido. | Resolved |
| R-03 | Minor | `UpdateProductRequest` | `bin` não estava declarado, então um deserializador padrão ignoraria o campo em silêncio em vez de gerar 422 `bin-immutable`. Verificado: `UpdateProductRequest.bin` (linha 201) agora é declarado com `type: string`, `readOnly: true` e a descrição pedida ("Somente leitura: a presença do campo no corpo é detectada e rejeitada com 422 bin-immutable, nunca ignorada"), consistente com a linha de correção FR1.4/AC1.3.2 e com a resposta da Q9/Q10. | Nenhuma — corrigido. | Resolved |

### Validation Tool Results

| Tool | Result | Interpretation |
|---|---|---|
| Parse de todos os blocos ```yaml (5 blocos: C6, C1, C2, C3, C4/C5) via PyYAML | PASS — os 5 blocos carregam sem erro | Nenhuma regressão de sintaxe YAML introduzida pelas três correções |
| grep de `"401"`/`"403"` no artefato | 11 ocorrências, todas via `$ref` para `Unauthorized`/`Forbidden` | Confirma R-01 aplicado em todos os endpoints dos três serviços, sem exceção |
| Checagem cruzada das unidades citadas (U1–U6) contra `unit-of-work.md` | Todas resolvem | Nenhuma regressão nas referências de unidade ao redor das áreas alteradas |

### Summary

As três correções pedidas (R-01, R-02, R-03) foram aplicadas exatamente como solicitado, de forma consistente nos três serviços, sem introduzir regressões sintáticas ou de referência nas áreas revisadas; nenhuma achado novo foi identificado nesta passada focada.
