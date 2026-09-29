# ADRs do CardForge

Formato do projeto: Contexto, Decisão, Consequências e Alternativas Rejeitadas. Os ADRs curtos têm uma ou duas linhas por seção; os de cache, retry/DLQ, outbox e idempotência são completos.

| ADR | Decisão | Formato |
|---|---|---|
| [0001](0001-java-21-spring-boot-3-5-monorepo.md) | Java 21, Spring Boot 3.5.x e Maven multimódulo, com plano de migração para 4.x | Curto, com plano |
| [0002](0002-keycloak-local-idp.md) | Keycloak como IdP local; o gateway é o único cliente | Curto |
| [0003](0003-lean-hexagonal-and-platform-module.md) | Hexagonal enxuta por serviço e módulo técnico `cardforge-platform` | Curto |
| [0004](0004-transactional-outbox.md) | Outbox transacional com relay interno | Completo |
| [0005](0005-issuance-idempotency.md) | Idempotência da emissão e do resultado | Completo |
| [0006](0006-product-cache-five-minute-window.md) | Cache de produto com janela de 5 minutos e lápide | Completo |
| [0007](0007-sqs-retry-and-dlq.md) | Retry pela SQS com backoff e DLQ | Completo |
| [0008](0008-pan-hmac-only.md) | PAN guardado só como HMAC e últimos 4 dígitos | Curto |
| [0009](0009-uniqueness-by-database-constraints.md) | Unicidade garantida por constraints e índices parciais | Curto |

Os cortes da R1 que afetam estas decisões estão na seção "Débitos e desvios conscientes" do `README.md` da raiz.
