# RAID Log: CardForge Release 1.0

Riscos, premissas, problemas e dependências identificados na avaliação de viabilidade. Base: `intent-statement.md`, `feasibility-questions.md` (Q1 a Q9), `product-brief.md` e `project.md`. Escala de probabilidade e impacto: Alta, Média ou Baixa.

## Riscos

| ID | Risco | Prob. | Impacto | Tratamento | Responsável |
|---|---|---|---|---|---|
| R1 | O escopo completo do brief não cabe em 7 dias de dedicação parcial | Alta | Alto | Mitigar: ordem de prioridade e lista de cortes na Definição de Escopo; esqueleto ponta a ponta primeiro; cortes registrados como débito (Q2) | Pessoa desenvolvedora |
| R2 | Pressão de prazo empurra atalhos em integridade ou nos testes críticos | Média | Alto | Evitar: esses itens estão fora da lista de cortes (Q2, C-O4) | Pessoa desenvolvedora |
| R3 | O comportamento de produção (latência, IAM, failover, durabilidade) difere do comprovado localmente | Média | Alto | Aceitar nesta release e registrar no README; validar em produção com o time de plataforma | Time de plataforma |
| R4 | Spring Boot 3.5 está fora do suporte OSS desde junho de 2026 | Alta | Médio | Aceitar e registrar em ADR com o plano de migração para 4.x (`project.md` Tech Stack) | Pessoa desenvolvedora |
| R5 | Metas de desempenho não medidas nesta release | Alta | Médio | Aceitar: declarar como metas não medidas; medir quando houver teste de carga | Pessoa desenvolvedora |
| R6 | Esgotamento da faixa de números por BIN | Baixa | Médio | Mitigar: métrica de ocupação com alerta em 70%; a expansão de faixas fica fora da R1 (brief §9) | Processamento da RPE |
| R7 | A rotação de chaves muda o identificador de unicidade do PAN | Média | Alto | Aceitar como débito documentado; o procedimento automatizado fica fora da R1 (brief §9, engineering-standards) | Pessoa desenvolvedora |
| R8 | Em produção, o serviço que guarda o PAN cifrado entra no escopo PCI; a conformidade depende de infraestrutura e processos fora desta release | Alta | Alto | Transferir: a release entrega os controles de aplicação (Q9); a conformidade fica com produção e operação | Segurança da Informação e Compliance/DPO (informados) |
| R9 | O direito de exclusão (LGPD) não é atendido na R1, e as regras proíbem exclusão física | Média | Médio | Aceitar como débito visível para a evolução (brief §9, `project.md` Forbidden) | Compliance/DPO (informado) |

## Premissas

| ID | Premissa | Como validar |
|---|---|---|
| A1 | Há Docker disponível na máquina de desenvolvimento e no CI (GitHub Actions) para o ambiente local e os testes de integração | Primeiro build do esqueleto e primeira execução de `./mvnw verify` no CI |
| A2 | A infraestrutura AWS de produção a ser provisionada é compatível com os serviços e contratos entregues (SQS Standard com DLQ, Redis, PostgreSQL, provedor de identidade OAuth2) | Revisão dos contratos e do README com o time de plataforma |
| A3 | O gateway de onboarding vai enviar `Idempotency-Key` no cadastro e, quando disponível, o ator original | Revisão dos contratos OpenAPI com o time do gateway |

## Problemas

| ID | Problema | Estado |
|---|---|---|
| I1 | A data de entrega inicialmente informada (29/09/2026) era inviável; corrigida para 05/10/2026 | Resolvido (Q8) |
| I2 | Houve pedido inicial de conformidade PCI-DSS completa, em conflito com o brief e com o escopo confirmado | Resolvido (Q9 substitui Q3) |

## Dependências

| ID | Dependência | Sobre quem | Afeta esta release? |
|---|---|---|---|
| D1 | Provisionamento da infraestrutura AWS de produção | Time de plataforma | Não: fora desta iniciativa, mas condiciona a ida à produção |
| D2 | Consumo dos contratos OpenAPI e envio de `Idempotency-Key` | Time do gateway de onboarding | Só a validação dos contratos; a integração real é posterior |
| D3 | Configuração do provedor de identidade corporativo em produção (clientes, escopos, audiência) | Plataforma e Segurança | Não: no ambiente local é simulado por Keycloak |
