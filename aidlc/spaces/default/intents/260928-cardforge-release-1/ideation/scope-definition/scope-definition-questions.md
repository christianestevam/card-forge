# Definição de Escopo: perguntas

Base: `intent-statement.md`, `feasibility-assessment.md` (veredito "viável com condições", entrega até 05/10/2026), `constraint-register.md` e as regras de `project.md` e `org.md`.

O objetivo desta etapa é sair com uma **ordem de prioridade e uma lista de cortes pré-aprovada**. Um ponto importante para as perguntas: quase tudo no brief já é obrigação registrada em algum lugar.

- **Mandated em `project.md`:** README completo e Postman sincronizado.
- **Decided em `project.md`:** alerta de ocupação do BIN.
- **Testing Posture em `org.md`:** CI e cobertura mínima de 80% para o escopo mvp, sem enfraquecimento.
- **`engineering-standards.md`:** OpenAPI e métricas.

Por isso a margem de corte está na **profundidade** de alguns itens, não na sua existência. A Q2 propõe essa lista.

Escolha uma letra (ou várias, onde indicado) na linha de resposta de cada pergunta.

## Q1. Este é o núcleo mínimo que nunca entra na lista de cortes?

Proposta, derivada do brief e das regras:

- Catálogo de produtos: criar, consultar, listar paginado, atualizar dados descritivos, cancelar.
- Cadastro de portador com `Idempotency-Key`, validações (CPF, idade, nome) e a solicitação de emissão publicada via outbox.
- Emissão assíncrona:
  - validação do produto com a regra dos 5 minutos e o cache;
  - geração do PAN;
  - resultado terminal persistido e publicado.
- Aplicação do resultado no cadastro e consulta consolidada com os quatro estados de completude.
- Consultas e mudanças de status de portador e cartão, com histórico de transições.
- Reconciliação de solicitações antigas.
- Autenticação OAuth2 com Keycloak local.
- Ambiente completo com um comando e `scripts/smoke-test.sh`.
- Os dez testes críticos, mais os testes de colisão de PAN e de concorrência na mesma `Idempotency-Key`.

A. Sim, este é o núcleo inegociável
B. Sim, mas com ajustes (especifique em X)
C. Not yet defined
X. Other (please specify)

[Answer]: B. Sim, com ajustes: incluir explicitamente no núcleo (1) retry com backoff via SQS e DLQ com redrive policy; (2) tratamento global de exceções com ProblemDetail e códigos HTTP semânticos; (3) contrato OpenAPI/Swagger por serviço; (4) collection do Postman sincronizada com os endpoints; (5) README com setup, diagrama Mermaid, decisões técnicas, comportamento sob falha e a explicação de como o sistema impede cartão para produto inexistente ou cancelado. Demais itens do núcleo conforme proposto. **Mode:** guided

## Q2. Quais destes cortes de profundidade ficam pré-aprovados, se o prazo apertar? (select all that apply)

Contexto: cada item continua existindo; só sai o que está além do mínimo. Cada corte feito vira débito conhecido no README (Q2 da Viabilidade).

A. Métricas: manter apenas as de emissão por status e motivo, pendências e idade do outbox, profundidade de filas e DLQs, hit/miss do cache e ocupação do BIN; deixar para depois o tempo de emissão, as colisões de PAN e o estado detalhado dos circuit breakers
B. ADRs: um ADR curto por decisão da seção Decided, com as alternativas em uma ou duas linhas cada, em vez de ADRs extensos
C. Runbooks de DLQ e reconciliação: procedimento enxuto no README em vez de documentos operacionais separados
D. Postman: cobrir o fluxo feliz, o token e a `Idempotency-Key`, mais os casos de erro das regras críticas, sem cobrir todos os códigos de erro de cada endpoint
E. None: nenhum corte pré-aprovado; decidir caso a caso
X. Other (please specify)

[Answer]: A, C, D. (A) Métricas mínimas; (C) runbooks de DLQ e reconciliação como procedimento enxuto no README; (D) Postman essencial: fluxo feliz, token, Idempotency-Key e erros das regras críticas. **Mode:** guided

## Q3. Em que ordem os cortes aprovados na Q2 devem ser aplicados?

A. Na ordem em que aparecem na Q2 (métricas, ADRs, runbooks, Postman)
B. Postman, runbooks, ADRs, métricas (preserva primeiro a observabilidade)
C. Runbooks, ADRs, métricas, Postman (preserva primeiro o material para o time do gateway)
D. Not applicable (nenhum corte aprovado)
X. Other (please specify)

[Answer]: A. Métricas → (ADRs) → runbooks → Postman **Mode:** guided

## Q4. As obrigações registradas nas regras continuam fora da lista de cortes?

Contexto: README completo, Postman sincronizado, CI com cobertura mínima de 80%, OpenAPI por serviço, propagação de correlationId e tracing, e um ADR por decisão registrada.

A. Sim: essas obrigações ficam, e só a profundidade (Q2) pode ser cortada
B. Não: aceito tirar algumas dessas obrigações desta release, com a regra ajustada em gate (especifique em X quais)
C. Not yet defined
X. Other (please specify)

[Answer]: A. Sim, ficam todas. Esclarecimentos: (1) a cobertura de 80% de linhas é medida por módulo de serviço, excluindo apenas a classe main e as classes de configuração; (2) tracing significa propagar correlationId e contexto W3C em HTTP e atributos SQS, sem incluir backend de tracing (Zipkin/Jaeger) no Compose; (3) ADR vale para decisões relevantes, no formato curto pré-aprovado na Q2, exceto cache, retry/DLQ, outbox e idempotência, que ficam completos. **Mode:** guided

## Q5. Depois do esqueleto ponta a ponta, qual ordem de construção você prefere?

Contexto: o primeiro incremento já está definido nas regras. É o fluxo completo token → produto → cadastro → emissão via fila, com cache → consulta consolidada, verificado pelo smoke test.

A. Risco primeiro: garantias de integridade e testes críticos de falha e concorrência (outbox, reentrega, emissões concorrentes, reconciliação), depois o restante das APIs
B. Valor primeiro: completar todas as APIs e regras de negócio, depois os testes críticos
C. Por serviço: fechar o catálogo, depois o cadastro, depois a emissão
D. Not yet defined
X. Other (please specify)

[Answer]: A. Risco primeiro, respeitando dependências: (1) emissão no card-service (resultado persistido, idempotência, outbox, regra dos 5 min com cache, PAN, retry/DLQ); (2) cadastro no cardholder-service (Idempotency-Key, outbox, consumidor de resultados) e reconciliação; (3) restante das APIs (listagem e atualização do catálogo, alterações de status com histórico, consulta consolidada completa); (4) observabilidade e documentação. Cada garantia é entregue junto com seus testes críticos, nunca deixados para o fim. **Mode:** guided

## Q6. Algum item tem prazo próprio antes de 05/10/2026?

A. Não: tudo tem o mesmo prazo, 05/10/2026
B. Sim: há entregas intermediárias (especifique em X)
C. Not yet defined
X. Other (please specify)

[Answer]: A. Não: tudo tem o mesmo prazo, 05/10/2026 **Mode:** guided

## Q7. Além da seção 9 do brief e da infraestrutura AWS de produção, confirma que também ficam fora desta release os itens abaixo? (select all that apply)

A. Teste de carga (as metas de desempenho ficam declaradas como não medidas)
B. Interface gráfica de qualquer tipo (a release é só de APIs)
C. Dashboards prontos de métricas (as métricas são expostas, mas não há painéis versionados)
D. None: nada além da seção 9 e da infraestrutura de produção
X. Other (please specify)

[Answer]: A, B, C, X. (A) Teste de carga; (B) interface gráfica; (C) dashboards de métricas; (X) Servidores de observabilidade no Compose (Prometheus, Grafana, Zipkin/Jaeger): os serviços expõem métricas e propagam o contexto de tracing, mas nenhum coletor ou backend é incluído. **Mode:** guided

## Q8. (Acompanhamento) ADRs em formato curto: é corte pré-aprovado ou formato padrão?

Contexto: na Q4 você disse que os ADRs de decisões relevantes seguem "o formato curto pré-aprovado na Q2", mas a opção B da Q2 (ADRs curtos) não foi marcada.

A. Formato padrão: ADRs curtos (alternativas em uma ou duas linhas) para decisões relevantes desde o início; cache, retry/DLQ, outbox e idempotência ficam completos
B. Corte pré-aprovado: ADRs completos por padrão, reduzidos ao formato curto só se o prazo apertar; cache, retry/DLQ, outbox e idempotência ficam sempre completos
C. Not yet defined
X. Other (please specify)

[Answer]: A. Sim, formato curto padrão, mantendo as quatro seções obrigatórias (Contexto, Decisão, Consequências e Alternativas Rejeitadas), cada uma em uma ou duas linhas; cache, retry/DLQ, outbox e idempotência ficam completos. (Primeira resposta repetiu o texto da Q4; confirmada nesta segunda resposta.) **Mode:** guided

## Consolidated Summary Confirmation

Resumo das respostas:

- Núcleo inegociável (Q1): o núcleo proposto, mais retry com backoff via SQS e DLQ com redrive policy; tratamento global de exceções com ProblemDetail e códigos HTTP semânticos; OpenAPI por serviço; collection do Postman sincronizada; README com setup, diagrama Mermaid, decisões, comportamento sob falha e como o sistema impede cartão para produto inexistente ou cancelado.
- Cortes de profundidade pré-aprovados (Q2): métricas mínimas; runbooks de DLQ e reconciliação como procedimento enxuto no README; Postman essencial.
- Ordem dos cortes (Q3, Q8): métricas mínimas, depois runbooks no README, depois Postman essencial (os ADRs curtos não são corte, são o formato padrão).
- Obrigações mantidas (Q4): README, Postman, CI com cobertura de 80% de linhas por módulo de serviço (excluindo só a classe main e as de configuração), OpenAPI, propagação de correlationId e contexto W3C em HTTP e SQS sem backend de tracing no Compose, ADR para decisões relevantes.
- Formato dos ADRs (Q8): curto por padrão, com Contexto, Decisão, Consequências e Alternativas Rejeitadas em uma ou duas linhas cada; cache, retry/DLQ, outbox e idempotência completos.
- Ordem de construção (Q5): esqueleto ponta a ponta; depois emissão no card-service; cadastro e reconciliação no cardholder-service; restante das APIs; observabilidade e documentação. Cada garantia é entregue com seus testes críticos.
- Prazos intermediários (Q6): nenhum; tudo até 05/10/2026.
- Fora da release (Q7): seção 9 do brief, infraestrutura AWS de produção, teste de carga, interface gráfica, dashboards de métricas e servidores de observabilidade no Compose (Prometheus, Grafana, Zipkin/Jaeger).

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
