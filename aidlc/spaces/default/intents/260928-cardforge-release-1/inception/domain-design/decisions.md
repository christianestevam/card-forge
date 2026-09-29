# Decisões de Arquitetura: Desenho de Domínio

ADRs no formato curto do projeto: Contexto, Decisão, Consequências e Alternativas Rejeitadas, em uma ou duas linhas cada. As decisões já registradas na seção Decided de `project.md` não são repetidas aqui. Os ADRs completos do projeto (cache, retry/DLQ, outbox e idempotência) são escritos em `docs/adr/` na Construção.

Base: `requirements.md`, `stories.md`, `team-practices.md` e `domain-design-questions.md` (Q1 a Q6).

## ADR-001: Componentes por responsabilidade de negócio dentro de cada serviço

- **Status:** Accepted
- **Contexto:** a divisão em três serviços já está decidida (brief §4). Dentro deles há responsabilidades com ritmos de mudança diferentes, e cada teste crítico precisa estar ligado a um bloco claro (`stories.md`).
- **Decisão:** 12 componentes, cada um dono de uma responsabilidade e das suas entidades (`components.md`), organizados em pacotes do mesmo módulo e sem interfaces cerimoniais (Q1).
- **Consequências:** limites testáveis e rastreáveis; exige disciplina de pacotes para não criar dependências fora do grafo.
- **Alternativas rejeitadas:** um componente por serviço, porque esconderia a reconciliação, o cache e o PAN dentro de blocos grandes.

## ADR-002: Origem do produto na consulta consolidada

- **Status:** Accepted
- **Contexto:** a consulta consolidada fica no cadastro, mas o cache de produto é do card-service. A resposta precisa sinalizar dado desatualizado com o instante (BR6.2) e o caso sem observação (Q13 das Histórias).
- **Decisão:**
  - O cadastro consulta o catálogo com timeout curto.
  - Em falha, usa a sua própria `ProductObservation` (gravada no cadastro e em cada consulta bem-sucedida), sinalizada como desatualizada sempre que não vier do catálogo na própria requisição (Q2, Q3).
  - Sem observação, o produto aparece como indisponível.
- **Consequências:** o cadastro não depende do card-service para o produto; a consulta faz uma chamada ao catálogo por requisição.
- **Alternativas rejeitadas:**
  - endpoint interno do card-service, que acopla a leitura à emissão;
  - leitura direta do Redis do card-service, que quebra a propriedade do cache.

## ADR-003: Detalhes do cartão por consulta síncrona ao card-service

- **Status:** Accepted
- **Contexto:** a consulta consolidada precisa dos detalhes do cartão emitido, e o card-service é o dono do cartão (Q4).
- **Decisão:** para `ISSUED`, consulta síncrona ao card-service com timeout. Em falha, a situação continua `ISSUED` com o `cardId`, e só os detalhes ficam indisponíveis. `PENDING` e `FAILED` não chamam o card-service.
- **Consequências:** os detalhes do cartão estão sempre atualizados, inclusive o status; a consulta depende parcialmente do card-service.
- **Alternativas rejeitadas:** copiar os detalhes para o cadastro a partir do resultado, porque ficariam desatualizados após bloqueio ou cancelamento sem um evento de status, que não existe na R1.

## ADR-004: Módulo técnico comum `cardforge-platform`

- **Status:** Accepted
- **Contexto:** o outbox, o envelope de evento, a propagação de correlationId e o handler base de ProblemDetail são idênticos nos serviços. Os padrões proíbem módulo de domínio compartilhado, e o `project.md` define um módulo Maven por serviço.
- **Decisão:** um quarto módulo Maven, `cardforge-platform`, só com esses itens técnicos (Q6). Ele não pode conter tipos de domínio nem regras de negócio, o que é verificado por ArchUnit, e é testado no próprio módulo com Testcontainers.
  - Fica embutido nos três serviços.
  - O outbox é ativado por auto-configuração condicional, só no cardholder-service e no card-service.
  - O product-service usa apenas a propagação de correlationId e o handler base de ProblemDetail (decidido na Geração de Unidades, Q6).
- **Consequências:**
  - uma única implementação dos mecanismos críticos, com um único conjunto de testes;
  - acoplamento de versão entre os serviços e esse módulo (aceitável num monorepo);
  - o módulo técnico adicional complementa a regra "um módulo por serviço", sem contradizê-la.
- **Alternativas rejeitadas:** duplicar o código em cada serviço, porque dobraria a superfície de testes do outbox, que é um mecanismo crítico.

## ADR-005: Ciclo assíncrono deliberado entre solicitação e emissão

- **Status:** Accepted
- **Contexto:** a solicitação vai do cadastro para a emissão, e o resultado volta pelo mesmo caminho (brief §4).
- **Decisão:** `IssuanceRequestTracker` e `IssuanceProcessor` dependem um do outro apenas por eventos (filas `card-issuance-requested` e `card-issuance-completed`), nunca por chamada síncrona.
- **Consequências:** é o único ciclo do grafo. Ele é seguro porque é assíncrono, idempotente dos dois lados e isolado por fila e DLQ.
- **Alternativas rejeitadas:** o card-service chamar o cadastro de forma síncrona para aplicar o resultado, o que acoplaria a disponibilidade dos dois serviços.

## ADR-006: Histórico de transições por agregado, incluindo a solicitação

- **Status:** Accepted
- **Contexto:** a regra ALWAYS exige histórico de transições com ator e instante. FR7.3 cita produto, portador e cartão.
- **Decisão:** cada componente dono de um agregado com status mantém o seu próprio histórico, na mesma transação da mudança. A solicitação de emissão também tem o seu (Q5).
- **Consequências:** a auditoria cobre também as decisões de emissão; são quatro tabelas de histórico, sem serviço de auditoria central.
- **Alternativas rejeitadas:** um histórico central, que exigiria um serviço novo fora do brief.
