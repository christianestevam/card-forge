# Mapa de Dependências Externas: CardForge Release 1.0

Base: `feasibility-assessment.md` e `raid-log.md` (D1 a D3), `contract-summary.md`, `bolt-plan.md` e `delivery-planning-questions.md`.

Um **Bolt** é uma passada de construção sobre uma parte do trabalho que termina em algo que roda.

Nenhum Bolt desta release depende de algo fora do projeto. O ambiente é local: o Keycloak substitui o provedor de identidade corporativo e o LocalStack substitui a SQS. A infraestrutura de produção está fora desta iniciativa. As dependências abaixo condicionam só a ida para a produção, depois da release.

| Dependência | Dono | Bloqueia algum Bolt? | O que acontece se atrasar |
|---|---|---|---|
| Provisionamento da infraestrutura AWS de produção | Time de plataforma | Não | A release é entregue localmente; a ida para a produção espera |
| Consumo dos contratos OpenAPI e envio de `Idempotency-Key` e `X-Actor-Id` | Time do gateway de onboarding | Não | A validação do contrato com o gateway acontece depois da entrega; a collection do Postman serve de referência |
| Configuração do provedor de identidade corporativo (clients, escopos, audiência) | Plataforma e Segurança | Não | O realm local versionado documenta a configuração esperada |
| Imagens públicas de Docker e dependências Maven | Registros públicos | Indiretamente (B1) | As versões ficam fixadas; uma indisponibilidade temporária atrasa o build, e o Dependabot e o Trivy acompanham |
