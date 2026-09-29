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
  - `CANCELED` é gravado e **nunca substituído**, porque `CANCELED` é terminal. Com a lápide, a emissão é recusada sem consultar o catálogo.
  - 404 é gravado como `NOT_FOUND`, mas não recusa sozinho.
- **Gravação atômica e condicional** por script Lua: vence a observação mais recente, e uma resposta `ACTIVE` antiga nunca desfaz uma lápide.
- **Degradação:**
  - Redis fora: consulta o catálogo e registra a degradação (`cardforge_product_cache_total{result="error"}`).
  - Redis e catálogo fora: falha técnica com retentativa.
  - 401, 403 ou contrato inválido: falha de configuração, nunca `PRODUCT_NOT_FOUND`.

## Consequências

- Com o catálogo fora, emissões continuam por até 5 minutos para produtos observados recentemente. Depois disso, ficam retidas com backoff até o catálogo voltar (TC6).
- Um cancelamento pode levar até 5 minutos para bloquear emissões, no limite aceito pela regra. Depois que o `card-service` grava a lápide, o bloqueio é imediato (TC7). Um cancelamento observado com instante anterior a uma observação `ACTIVE` já guardada é recusado pela gravação condicional; nesse caso, o bloqueio acontece quando a observação `ACTIVE` vence, ainda dentro dos 5 minutos. O endurecimento (cancelamento sempre prevalecendo sobre `ACTIVE`) está registrado nas limitações conhecidas do README.
- O cache não serve à consulta consolidada: ela consulta o catálogo (ADR-002 do Desenho de Domínio).

## Alternativas rejeitadas

- **`@Cacheable` com TTL de 5 minutos:** a leitura do Spring Cache não permite a regra "ler não renova", e não há como impedir que uma resposta antiga sobrescreva um cancelamento.
- **Remover o registro no cancelamento:** foi a versão do B1. Uma resposta `ACTIVE` que chegasse depois restaurava o produto por mais 5 minutos (os testes da lápide falharam contra ela).
- **Evento de cancelamento do catálogo para o `card-service`:** exige outro contrato assíncrono e um produtor no `product-service`; ficou fora da R1.
- **Consultar o catálogo em toda emissão:** acopla a disponibilidade e a latência da emissão ao catálogo.

## Segurança

O cache guarda só dados públicos do produto (nome, BIN e status), sem dados pessoais.
