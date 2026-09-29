# Prompts principais

Os prompts que conduziram o projeto, em ordem. O contexto que eles referenciam (product brief, padrões de engenharia e regras do projeto) foi escrito antes e está versionado em `aidlc/`. As respostas dadas em cada gate do AI-DLC estão nos arquivos `*-questions.md` de cada estágio, em [`aidlc/spaces/default/intents/260928-cardforge-release-1/`](../aidlc/spaces/default/intents/260928-cardforge-release-1/). Contexto do processo: [`PROCESSO.md`](PROCESSO.md).

## 1. Início do workflow AI-DLC (Claude Code)

Registrado na auditoria do intent (`audit/`) e em `project-description.json`:

```text
/aidlc mvp Implementar a Release 1.0 do CardForge, plataforma de emissão de cartões da RPE, conforme product-brief.md e engineering-standards.md em aidlc/spaces/default/knowledge/aidlc-shared/ e as decisões registradas em project.md. Três microsserviços (product-service, cardholder-service, card-service) integrados por REST e SQS, com cache Redis. A release vai para produção com prazo curto e uma pessoa desenvolvendo: construa apenas o que o brief exige e priorize integridade dos dados, emissão idempotente e comportamento definido sob falha de cada dependência.
```

## 2. Exemplos de correções nos gates

Nos gates, as respostas nem sempre foram uma das opções propostas. Quando a opção era imprecisa, escrevi a resposta. Dois exemplos que viraram regras do projeto:

```text
ALWAYS descrever a garantia de desfecho como: cada solicitação tem no máximo um desfecho terminal, que nunca é reavaliado; enquanto não o tiver, permanece rastreável e recuperável, com alerta. NEVER prometer que toda solicitação chega a um desfecho.
```

```text
ALWAYS responder 422 com a lista de campos inválidos para toda validação de valor no corpo da requisição, em todos os serviços; 400 apenas para JSON malformado, tipos incompatíveis e headers inválidos. NEVER ignorar em silêncio a tentativa de alterar um atributo imutável: responder 422 com type próprio.
```

## 3. Construção: Bloco 1 (fora do workflow, com plano aprovado antes do código)

```text
Não use o workflow /aidlc nesta sessão. Leia os artefatos em aidlc/spaces/default/intents/260928-cardforge-release-1/inception/ (requirements.md, components.md, contract-summary.md, decisions.md, unit-of-work.md) e aidlc/spaces/default/memory/project.md. Implemente o Bloco 1: monorepo Maven (cardforge-platform, product-service, cardholder-service, card-service); docker-compose.yml com PostgreSQL (um database por serviço), Redis, LocalStack (filas, DLQs e redrive policy), Keycloak (realm importado) e os três serviços com healthcheck; fluxo mínimo ponta a ponta: token → criar produto → cadastrar portador → evento SQS → cartão emitido consultando o produto com cache Redis → consulta consolidada; e scripts/smoke-test.sh. Apresente o plano antes de escrever código.
```

O primeiro plano trouxe todas as garantias e gates originais. Pelo prazo, pedi cortes explícitos (credenciais de desenvolvimento, PAN só em HMAC, sem `Idempotency-Key`, sem histórico de transições, gates de qualidade reduzidos), e o agente pediu confirmação por eles contrariarem as regras do projeto:

```text
Confirmo: os cortes são meus e valem sobre essas regras nesta construção, por restrição de prazo. Dois ajustes:
(1) Mantenha a estratégia de branch do team.md: um branch por bloco (feat/walking-skeleton), com merge em main só com o CI verde; o squash é opcional.
(2) traceparent: mantenha se for apenas configuração (Micrometer Tracing com propagação automática no RestClient e no Spring Cloud AWS); se exigir código próprio, corte.
Os demais cortes ficam como estão. Não altere o team.md. No project.md, acrescente só uma nota no topo dizendo que, na construção da R1, os desvios listados em "Débitos e desvios conscientes" do README prevalecem sobre estas regras. Refaça o plano e me mostre antes de codar.
```

Na aprovação do plano revisado:

```text
Aprovado, com estes acréscimos (a entrega é hoje à tarde, então este bloco é a entrega completa):
1) springdoc-openapi com Swagger UI nos três serviços.
2) Retry com backoff exponencial simples no consumidor do card-service via Visibility do Spring Cloud AWS (30 s dobrando, teto de 5 min); falha de negócio (produto inexistente ou cancelado) grava FAILED e confirma, sem retry.
3) Testes de integração de falha: (a) catálogo retornando 5xx ou timeout → mensagem não confirmada, nenhum cartão; (b) produto CANCELED → FAILED sem retry; (c) mesma mensagem entregue duas vezes → um único cartão; (d) SQS indisponível no cadastro → 202, evento fica no outbox e é publicado quando o SQS volta.
4) Mude a ordem: logo depois do platform, suba no Compose só a infraestrutura (Postgres, Redis, LocalStack, Keycloak) e valide o realm e as filas; os serviços entram no Compose depois.
5) README com as seções: setup, arquitetura em Mermaid, decisões técnicas (estratégia de cache com a janela de 5 min, outbox, idempotência, retry/DLQ), como o sistema impede cartão para produto inexistente ou cancelado, comportamento com SQS fora e com o catálogo offline, e Débitos e desvios conscientes.
6) Postman com o fluxo feliz e os casos de erro principais (422, 409, 404).
```

## 4. Construção: Blocos 2 e 3

```text
Decisões para o B2:
1) D4 e D5 continuam cortados. Sem Idempotency-Key e sem histórico de transições; as transições de status só atualizam updatedAt. Corte também a reconciliação automática: no README fica o procedimento manual (redrive da DLQ e consulta das solicitações PENDING antigas).
2) Sim à prova por mutação, mas só nos testes de maior valor: mensagem duplicada gera um único cartão; colisão de PAN com ON CONFLICT; recusa de negócio sem retry; e cadastro com SQS fora publicado depois pelo outbox. Registre no commit qual proteção foi desligada e a mensagem de falha observada. A lápide é escrita antes da implementação.
3) Ordem de trabalho: (a) lápide do cache com teste; (b) degradação da consulta consolidada com catálogo offline e com card-service offline, com testes; (c) block, unblock e cancel do cartão, com 200 idempotente para o status atual; (d) block, unblock e cancel do portador.
```

```text
Decisões para o B3: ordem de trabalho para proteger os entregáveis obrigatórios: (a) POST /products/{id}/cancel com teste, incluindo o IT com Clock controlado mostrando que a emissão para depois de 5 min; (b) README conferido contra a lista obrigatória do project.md; (c) Postman: cancelamento de produto e os erros 409 e 422; (d) GET /products paginado; (e) PATCH /products/{id} com bin-immutable; (f) caso STALE com product_observations. Cancelar produto já cancelado responde 200 sem mudança, seguindo o contrato C1. Sem job de smoke test no CI: registrado como débito.
```

## 5. Revisão pré-entrega e Bloco 4

Prompt da revisão independente, com o enunciado anexado:

```text
O fluxo de desenvolvimento já foi feito, o que quero que você faça agora é atuar como referência: imagine que você seja o head de engenharia de software da empresa e está avaliando esse projeto, pois ele é um teste técnico. Levante pontos de observação, melhorias de código, design, algo que não faça sentido; avalie tudo para que a gente possa corrigir antes de enviar. Estou enviando em anexo também o teste técnico em si para que você analise e veja se cobrimos todos os pontos.
```

Decisões enviadas ao agente para o B4:

```text
1) Ordem de trabalho: (1) R3; (2) R2; (3) README reorganizado; (4) R1; (5) R7 e as melhorias 3 e 4; (6) R5 e R6; (7) R4; (8) melhorias 1, 2 e 7. O que não for feito entra numa seção "Limitações conhecidas" do README. Teste escrito antes e visto falhando para R1, R2 e R3; nos demais, teste de regressão junto com a correção.
2) R3: implemente a seção product no GET /cards/{id}, lida do Redis em qualquer idade (com observedAt e availability CURRENT ou STALE) ou do catálogo, e UNAVAILABLE se não houver nenhum dos dois. Cartão de produto cancelado continua consultável. É só leitura e não altera a regra de emissão.
3) Mantenha aidlc/. O README abre com objetivo, como rodar, roteiro de demonstração e a matriz do enunciado; a narrativa do processo vai para docs/PROCESSO.md.
4) Antes de publicar o repositório: confira que nada sensível está versionado.
```

Uma segunda rodada de revisão propôs endurecer ainda mais o cache (bloco B5). A decisão foi não executá-lo antes da entrega:

```text
Não vamos executar o B5 antes da entrega; o código está congelado. Acrescente ao README, na seção "Limitações conhecidas", os pontos do diagnóstico do cache, cada um com impacto, janela e correção prevista. Apenas documentação, sem nenhuma alteração de código.
```
