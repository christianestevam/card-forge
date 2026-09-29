# ADR-0006: Cache de produto com janela de 5 minutos e lápide

- **Status:** Accepted (2026-09-27; lápide implementada no B2)

## Contexto

A emissão não pode acontecer para um produto inexistente ou cancelado além de uma janela de 5 minutos (BR4.1, NFR7), e o catálogo pode ficar indisponível. Consultar o catálogo em cada emissão acopla a disponibilidade da emissão à do catálogo; confiar num cache sem limite de idade viola a regra.

## Decisão

- O `card-service` é a única autoridade sobre a elegibilidade (`ProductEligibility`), com um adaptador de cache explícito (sem `@Cacheable`).
- **Um registro por produto** em `cardforge:product:v1:{productId}` (JSON: `productId`, `name`, `bin`, `status`, `validatedAt`, `validatedAtMillis`), com TTL físico de 24 h.
- **Emissão:** usa o registro só se for `ACTIVE` com `validatedAt` de no máximo 5 minutos (inclusivo, com `Clock` injetado). Ler nunca renova `validatedAt`.
- **Catálogo:** uma tentativa por processamento, com timeout de 1 s para conectar e 2 s para ler. O instante gravado é o anterior à chamada (conservador).
- **Lápide:**
  - `CANCELED` recebido prevalece sobre `ACTIVE`/`NOT_FOUND` independentemente do timestamp de início da consulta; uma vez gravado, **nunca é substituído**, porque `CANCELED` é terminal. Com a lápide, a emissão é recusada sem consultar o catálogo.
  - 404 é gravado como `NOT_FOUND`, mas não recusa sozinho.
- **Resolução atômica** por Lua (`mergeAndGet`): compara, grava quando necessário e retorna o JSON vencedor na mesma execução. Emissão e consulta usam esse resultado, sem um segundo GET sujeito a outra corrida. Fora da precedência terminal de CANCELED, vence a observação mais recente; em empate de milissegundos, NOT_FOUND prevalece sobre ACTIVE.
- **Validade na transação:** a emissão relê o relógio antes de criar o cartão e depois de todas as escritas, inclusive outbox. Observação vencida causa rollback completo e revalidação fora da transação, com tentativas limitadas. Decisões já persistidas continuam sendo republicadas sem revalidação.
- **Consulta básica:** `GET /cards/{id}?includeProduct=false` lê apenas o banco de cartões. O overview usa essa opção, pois já consulta o catálogo separadamente; o GET público continua enriquecido por padrão.
- **Degradação:**
  - Redis fora: consulta o catálogo e registra a degradação (`cardforge_product_cache_total{result="error"}`).
  - Redis e catálogo fora: falha técnica com retentativa.
  - 401, 403 ou contrato inválido: falha de configuração, nunca `PRODUCT_NOT_FOUND`.

## Consequências

- Com o catálogo fora, emissões continuam por até 5 minutos para produtos observados recentemente. Depois disso, ficam retidas com backoff até o catálogo voltar (TC6).
- Um cancelamento ainda não observado pode levar até a janela de cinco minutos para impedir uma decisão de emissão. O cancelamento conhecido vence na resolução atômica, inclusive se veio de uma chamada iniciada antes do ACTIVE guardado.
- A validação temporal final ocorre depois das operações bloqueantes de escrita e antes de encerrar a transação. Ela não oferece um prazo matemático para o commit físico sob pausas arbitrárias, nem atomicidade entre Redis, HTTP e PostgreSQL.
- A observação devolvida pelo Redis venceu naquele instante; um cancelamento posterior pode ocorrer. Expiração/reinício/perda do Redis também apaga a lápide; nessa situação o catálogo é consultado como autoridade.
- A consulta mantém a política de apresentar observações antigas como STALE. Sua idade não concede autorização para emitir.
- O cache não serve à consulta consolidada: ela consulta o catálogo (ADR-002 do Desenho de Domínio).

## Alternativas rejeitadas

- **`@Cacheable` com TTL de 5 minutos:** a leitura do Spring Cache não permite a regra "ler não renova", e não há como impedir que uma resposta antiga sobrescreva um cancelamento.
- **Remover o registro no cancelamento:** foi a versão do B1. Uma resposta `ACTIVE` que chegasse depois restaurava o produto por mais 5 minutos (os testes da lápide falharam contra ela).
- **Evento de cancelamento do catálogo para o `card-service`:** exige outro contrato assíncrono e um produtor no `product-service`; ficou fora da R1.
- **Consultar o catálogo em toda emissão:** acopla a disponibilidade e a latência da emissão ao catálogo.

## Segurança

O cache guarda só dados públicos do produto (nome, BIN e status), sem dados pessoais.
