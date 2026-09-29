# ADR-0003: Hexagonal enxuta e módulo técnico comum

- **Status:** Accepted (Desenho de Domínio, ADR-001 e ADR-004)
- **Contexto:** três serviços com regras distintas e mecanismos técnicos idênticos (outbox, envelope, correlationId, ProblemDetail).
- **Decisão:** cada serviço tem `domain` (sem frameworks, verificado por ArchUnit), `application`, `infrastructure` e `web`, com entidades JPA separadas do domínio. O código técnico comum fica em `cardforge-platform`, sem tipos de domínio (também verificado por ArchUnit).
- **Consequências:** regras testáveis sem Spring e uma única implementação do outbox. Em troca, há acoplamento de versão entre os serviços e o módulo comum (aceitável num monorepo).
- **Alternativas rejeitadas:** módulo de domínio compartilhado, proibido pelos padrões; duplicar o outbox em cada serviço, que dobraria a superfície de testes de um mecanismo crítico.
