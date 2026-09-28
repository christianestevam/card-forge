# Declaração de Intenção: CardForge Release 1.0

## Declaração do problema (Problem Statement)

A RPE precisa de uma plataforma própria de emissão de cartões em que o cadastro do cliente e a emissão do cartão aconteçam separadamente, para que uma adesão nunca dependa da emissão estar disponível naquele momento. [Q1] [Q4] [desc]

A garantia de negócio é que nenhuma solicitação aceita se perde. Cada solicitação tem no máximo um desfecho final (cartão emitido ou recusa explicada), que nunca é revisto depois de tomado. Enquanto não tiver desfecho, a solicitação continua rastreável e recuperável, e a equipe responsável é alertada. [Q1] [Q9]

A release deve priorizar a integridade dos dados, a garantia de que uma mesma solicitação nunca gera dois cartões e um comportamento definido quando algum sistema de que a plataforma depende falhar. [desc] [Q3]

## Cliente-alvo (Target Customer)

Cliente principal: varejistas parceiros (private label e cobranded) e seus consumidores, atendidos via gateway de onboarding da RPE. [Q2]

A dor do cliente principal é perder ou travar adesões nos picos de cadastro, com o consumidor esperando no checkout. [Q2]

Interessado interno: a unidade de Processamento da RPE, que opera a plataforma e precisa de emissão íntegra, rastreável e recuperável, sem perda nem duplicidade. [Q2]

## Métricas de sucesso (Success Metrics)

| Métrica | Critério de aceite | Fonte |
|---|---|---|
| Solicitações perdidas | Nenhuma solicitação aceita se perde por falha entre banco e mensageria | [Q3] |
| Desfecho único | Cada solicitação tem no máximo um desfecho terminal, que nunca é reavaliado | [Q3] |
| Solicitações sem desfecho | Permanecem rastreáveis e recuperáveis, com alerta | [Q3] |
| Duplicidade | Zero cartões duplicados por solicitação | [Q3] |
| Produto inválido | Zero emissões para produto inexistente ou cancelado além da janela de 5 minutos | [Q3] |
| Cadastro sob falha | O cadastro segue aceito com catálogo, mensageria ou Redis indisponíveis | [Q3] |
| Comportamento sob falha | Comportamento sob falha de cada dependência comprovado pelos testes críticos automatizados | [Q3] |
| Latência do cadastro (meta de projeto) | p95 < 300 ms | [Q3] |
| Tempo de emissão (meta de projeto) | p95 < 5 s e p99 < 60 s, com dependências saudáveis | [Q3] |
| Latência das consultas de cartão e produto (meta de projeto) | p95 < 200 ms | [Q3] |
| Disponibilidade (meta de produção) | 99,9% por serviço; depende da infraestrutura de produção e não é verificável nesta release | [Q3] [Q10] |
| Demonstração local | Tudo sobe com um único comando, e o fluxo ponta a ponta é verificado automaticamente | [Q3] |

## Gatilho da iniciativa (Initiative Trigger)

Colocar em produção a primeira release da plataforma de emissão, com prazo curto. [Q4] [desc]

Os picos sazonais de adesão (Black Friday, Natal, campanhas) exigem um cadastro que não dependa de a emissão estar disponível. [Q4]

## Sinal inicial de escopo (Initial Scope Signal)

Escopo selecionado pelo workflow (workflow-selected): `mvp`. [scope]

Fronteira de produto confirmada por você: a Release 1.0 como descrita no brief, incluindo o ambiente local em Docker Compose. [Q8]

Ficam fora da fronteira confirmada os itens da seção 9 do brief e a infraestrutura AWS de produção. [Q8] [memory:M3]

A release tem prazo curto e é desenvolvida por uma pessoa; cada componente se justifica por um requisito do product brief e, na dúvida, não se constrói. [memory:M1] [desc]

Sugestões novas só viram obrigação com aprovação em gate. [memory:M2]

## Assumptions & Open Questions

None.
