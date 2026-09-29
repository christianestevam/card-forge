# Desenho de Domínio: perguntas

Base: `requirements.md`, `stories.md` (incluindo as questões encaminhadas), `team-practices.md`, `product-brief.md` §4 e `engineering-standards.md`.

Já estão decididos e não serão perguntados:
- os três serviços e o dono de cada entidade principal (brief §4: Product no catálogo, Cardholder e a solicitação no cadastro, Card no card-service);
- a arquitetura hexagonal enxuta;
- o outbox, o cache no card-service e o resultado em `issuance_processing` (seção Decided de `project.md`).

Esta etapa define os blocos lógicos dentro disso e fecha a questão que as Histórias de Usuário marcaram como bloqueante para a Geração de Unidades: de onde vem o produto na consulta consolidada.

Escolha uma letra na linha de resposta de cada pergunta.

## Q1. Com que granularidade descrever os componentes?

A. Por responsabilidade de negócio dentro de cada serviço. Exemplos:
   - catálogo: ProductCatalog;
   - cadastro: CardholderRegistry, IdempotencyGuard, IssuanceRequestTracker, IssuanceReconciler e ConsolidatedViewComposer;
   - emissão: IssuanceProcessor, ProductEligibility, PanGenerator, PanProtector e CardLifecycle;
   - em cada serviço que publica: um OutboxRelay.
B. Um componente por serviço (três componentes grandes)
C. Not yet defined
X. Other (please specify)

[Answer]: A

## Q2. De onde a consulta consolidada (no cardholder-service) obtém o produto?

Contexto: o cache de produto é do card-service (brief §4), mas a consulta consolidada fica no cardholder-service e precisa sinalizar dado desatualizado com o instante da observação (BR6.2) e o caso sem observação (Q13).

A. O cardholder-service consulta o catálogo com timeout curto. Em caso de falha, usa a última observação que ele próprio guardou (no cadastro ou na última consulta bem-sucedida), com o instante; sem nenhuma observação, o produto aparece como indisponível
B. O card-service expõe o produto do seu cache por um endpoint interno, e o cardholder-service o consulta
C. O cardholder-service lê diretamente o cache Redis do card-service
D. Not yet defined
X. Other (please specify)

[Answer]: A

## Q3. A partir de que idade a observação do produto aparece como "desatualizada" na consulta consolidada?

A. Mais de 5 minutos, a mesma janela da emissão
B. Toda observação que não veio de uma consulta ao catálogo nesta mesma requisição, com o instante informado
C. Mais de 24 horas
D. Not yet defined
X. Other (please specify)

[Answer]: B

## Q4. De onde a consulta consolidada obtém os detalhes do cartão emitido?

A. Consulta síncrona ao card-service (cartão por ID) com timeout; se ele falhar, a situação continua `ISSUED` com o `cardId` e só os detalhes aparecem como indisponíveis (AC4.2.3)
B. O cardholder-service guarda uma cópia dos detalhes do cartão recebida no resultado da emissão, sem consultar o card-service
C. Not yet defined
X. Other (please specify)

[Answer]: A

## Q5. A mudança de situação da solicitação (`PENDING` → `ISSUED`/`FAILED`) entra no histórico de transições?

Contexto: a regra ALWAYS pede histórico das transições de status com ator e instante. FR7.3 cita produto, portador e cartão, mas não a solicitação.

A. Sim: a solicitação tem histórico próprio no cadastro, com o ator técnico (o consumidor de resultados) e o instante
B. Não: o histórico cobre só produto, portador e cartão; o instante da decisão já fica no resultado
C. Not yet defined
X. Other (please specify)

[Answer]:A

## Q6. Código técnico repetido entre serviços (outbox, envelope de evento, correlationId, tratamento de erros): como organizar?

Contexto: os padrões de engenharia pedem para evitar módulo de domínio compartilhado entre serviços.

A. Duplicar em cada serviço, sem módulo comum, nesta release (YAGNI)
B. Um módulo técnico comum só para infraestrutura (outbox, envelope, correlationId), sem nada de domínio
C. Not yet defined
X. Other (please specify)

[Answer]: X — B, com limites: um módulo técnico comum (ex.: cardforge-platform) contendo apenas outbox (tabela e relay), envelope de evento, propagação de correlationId e o handler base de ProblemDetail; proibido conter tipos de domínio ou regras de negócio, o que é verificado por ArchUnit; testado no próprio módulo com Testcontainers.

## Consolidated Summary Confirmation

Resumo das respostas:

- Granularidade (Q1): componentes por responsabilidade de negócio dentro de cada serviço (ProductCatalog; CardholderRegistry, IdempotencyGuard, IssuanceRequestTracker, IssuanceReconciler, ConsolidatedViewComposer; IssuanceProcessor, ProductEligibility, PanGenerator, PanProtector, CardLifecycle; OutboxRelay onde há publicação).
- Produto na consulta consolidada (Q2): o cardholder-service consulta o catálogo com timeout curto; em falha, usa a última observação que ele mesmo guardou (no cadastro ou na última consulta bem-sucedida), com o instante; sem observação, produto indisponível.
- Desatualizado (Q3): toda observação que não veio de uma consulta ao catálogo na própria requisição, sempre com o instante.
- Cartão na consulta consolidada (Q4): consulta síncrona ao card-service com timeout; em falha, continua `ISSUED` com o `cardId` e só os detalhes ficam indisponíveis.
- Histórico da solicitação (Q5): a mudança de situação da solicitação entra em histórico próprio no cadastro, com ator técnico e instante.
- Código técnico comum (Q6): um módulo técnico comum (ex.: `cardforge-platform`) apenas com outbox (tabela e relay), envelope de evento, propagação de correlationId e handler base de ProblemDetail; proibido conter tipos de domínio ou regras de negócio, verificado por ArchUnit; testado no próprio módulo com Testcontainers. Observação: é um quarto módulo Maven além dos três serviços.

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
