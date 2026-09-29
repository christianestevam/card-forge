**Collaborator:** aidlc-quality-agent

## Contribution

Revisão independente da postura de testes do rascunho do lead (`team-practices.md` § Testing Posture e `evidence.md` U4–U6), contra `org.md` § Testing Posture, `project.md` § Testing Posture / Tech Stack / Forbidden, `engineering-standards.md` § Qualidade e entrega, `product-brief.md` §5 e §7, e `scope-document.md` (núcleo, sequência de construção e Q7). Estado relevante: `Scope: mvp`, `Test Strategy: Standard`, `Construction Iteration: unit-major`, `Construction Checkpoints: enabled`. Repositório greenfield: não há `pom.xml`, JaCoCo, Surefire/Failsafe nem workflow de CI para inspecionar; tudo abaixo é proposta a confirmar, não prática observada.

### 1. Metodologia e ordem (U4)

O padrão da org sem postura afirmada é `test-after` por camada. O projeto, porém, exige que os dez testes críticos "provem um comportamento, não apenas a execução do código" (`project.md`). Um teste escrito depois da implementação e que nunca foi visto falhar não prova isso: ele pode passar por acaso (ex.: a unicidade é garantida pelo `@Version`, não pelo índice parcial; o teste passa mesmo sem o `ON CONFLICT`). Por isso proponho três opções para a entrevista, em ordem de recomendação:

- **(A) `custom` — recomendada.** Os testes críticos e os obrigatórios adicionais (colisão de PAN, concorrência na `Idempotency-Key`) de cada garantia são escritos antes da implementação dessa garantia e vistos falhando (vermelho pelo motivo certo); os unitários e os demais testes de integração vêm depois de cada camada. Frase de `Ordering` sugerida: "Para cada garantia, escrevemos primeiro os seus testes críticos e os vemos falhar, depois implementamos cada camada testável e escrevemos e rodamos os testes dessa camada, e o incremento só fecha com todos os testes da garantia verdes."
- **(B) `test-after` com prova de falha.** Mantém o padrão da org, mas cada teste crítico registra uma "verificação de sabotagem": remover temporariamente a proteção que ele cobre (índice parcial, gravação no outbox, checagem de `validatedAt`, etc.) e confirmar que ele fica vermelho, anotando no commit ou na descrição do PR qual proteção foi removida. Custo parecido com (A), só muda o momento.
- **(C) `test-after` puro** (rascunho atual). Mais barato, mas não entrega a prova de comportamento que o `project.md` exige para os testes críticos.

`tdd` em todo o código não é recomendado: prazo de 05/10/2026 com uma pessoa, e o ganho marginal em adaptadores e mapeamentos JPA é baixo. Mutação (pitest) também fica fora: caro para a release; a sabotagem dirigida cobre o risco nos pontos que importam.

### 2. Cobertura (U5)

Concordo com o piso de 80% de linhas por módulo de serviço, excluindo só a classe main e as classes de configuração, verificado no `verify` e no CI. Para que o piso seja executável e não vire brecha, a entrevista precisa fixar:

- **Mecânica:** `jacoco:prepare-agent` (Surefire) e `jacoco:prepare-agent-integration` (Failsafe), `jacoco:merge` dos dois `.exec`, e `jacoco:check` no `verify`, depois do `failsafe:integration-test`. Sem o merge, os adaptadores (JPA, SQS, Redis, HTTP), cobertos só por testes de integração, derrubam a cobertura e induzem testes unitários artificiais. Regra: contador `LINE`, `COVEREDRATIO` mínimo `0.80`, elemento `BUNDLE`, em cada um dos três módulos (`product-service`, `cardholder-service`, `card-service`).
- **Exclusão operacional:** o JaCoCo exclui por padrão de nome de classe, não por anotação. "Classes de configuração" precisa virar convenção de código, por exemplo: `**/*Application.class` e classes em um pacote `**/config/**` (ou sufixo `*Config`/`*Configuration`). A lista de exclusões é fechada; qualquer ampliação passa por gate, como o próprio piso (`org.md`: o piso não pode ser enfraquecido para uma etapa passar).
- **Branches:** recomendo apenas reportar a cobertura de branches, sem piso na R1. A proteção real das garantias vem dos testes críticos com prova de falha, não de um segundo percentual.
- **Desde quando:** o `jacoco:check` vale a partir do primeiro incremento (esqueleto). Se o esqueleto ficar abaixo de 80%, completa-se o teste, não se rebaixa o piso.

### 3. Portões de qualidade no CI (U6)

- **Portão único local e no CI:** `./mvnw verify`, rodando Surefire (`*Test`), Failsafe (`*IT`), `jacoco:check` e `spotless:check` (formatação barata, falha bloqueia). Os runners `ubuntu-latest` do GitHub Actions têm Docker, então Testcontainers roda sem ajuste.
- **Smoke test no CI:** apoio a sugestão do lead de rodar `scripts/smoke-test.sh` em um job separado, depois do `verify`, subindo o Compose no runner. É o comando de verificação da Construction; rodá-lo no CI evita que ele só funcione na máquina do desenvolvedor. Ponto a decidir: obrigatório em todo PR para `main` ou só no push em `main`.
- **Sem retentativa automática de teste:** nada de `rerunFailingTestsCount` no Surefire/Failsafe nem de retentativa de job. Os testes de concorrência (3, 5 e `Idempotency-Key`) são exatamente os que ficariam intermitentes se houver bug real de corrida; reexecutar até passar esconderia o defeito. Teste instável é corrigido, não reexecutado.
- **Evidência:** publicar como artefato do job os relatórios JUnit XML e o relatório HTML do JaCoCo de cada módulo.
- **Quando nasce:** junto com o esqueleto (como sugere o `scope-document.md`), para que todo incremento seguinte já passe pelo portão.

### 4. Padrões de testes de integração

- **Nomenclatura:** `*Test` para unitários (Surefire), `*IT` para integração (Failsafe).
- **Infraestrutura real:** Testcontainers com PostgreSQL, Redis e LocalStack (SQS), com as mesmas tags de imagem do `docker-compose.yml` (nunca `latest`); nada de H2, Redis embarcado ou fila em memória (`project.md` Forbidden). Containers compartilhados por módulo (singleton, `@ServiceConnection`), com schema criado pelo Flyway e `spring.jpa.hibernate.ddl-auto=validate`, para que os índices parciais testados sejam os de produção.
- **HTTP externo:** WireMock para o catálogo (product-service visto pelo card-service e pelo cardholder-service) e para o card-service visto pela consulta consolidada. Falhas simuladas com os recursos do WireMock (atraso acima do read timeout, `CONNECTION_RESET_BY_PEER`, 5xx, 401/403, contrato inválido), cobrindo a classificação de respostas de `engineering-standards.md` e o Forbidden de nunca virar `PRODUCT_NOT_FOUND`.
- **Autenticação nos testes:** Keycloak em Testcontainers é pesado; proposta: `spring-security-test` (`jwt()`) nos testes de camada web e um JWKS servido pelo WireMock com chave de teste nos ITs completos (validando emissor, audiência e escopos). O Keycloak real é exercitado pelo smoke test.
- **Tempo:** `Clock` mutável injetado nos testes para a janela de 5 minutos, retenção de 24 h da `Idempotency-Key`, idade mínima da reconciliação e validade do cartão. Proibido `Thread.sleep` para esperar regra de tempo; asserções assíncronas usam Awaitility com timeout explícito. O cálculo de backoff (30 s dobrando, jitter ±20%, teto de 5 min após o jitter) é testado como função pura com fonte de aleatoriedade injetada; o IT só verifica que o `ChangeMessageVisibility` foi aplicado, sem esperar o intervalo real.
- **Semântica de commit:** testes críticos e de outbox nunca usam `@Transactional` com rollback no teste. Esse rollback esconde exatamente o que eles precisam provar (commit antes do ACK, atomicidade cartão + resultado + outbox). Isolamento por dados únicos (gerador de CPF válido, IDs aleatórios) ou limpeza explícita de tabelas.
- **Falhas destrutivas:** testes que derrubam uma dependência (ex.: teste 1, SQS indisponível) usam container próprio ou pausam um container dedicado; nunca pausam o singleton compartilhado, para não contaminar outros testes.
- **Concorrência:** testes 3, 5 e `Idempotency-Key` disparam as threads juntas com `CyclicBarrier`/`CountDownLatch` e verificam o invariante no banco (uma linha, um cartão, um resultado), repetidos algumas vezes no mesmo teste para aumentar a chance de sobreposição real.
- **Dados sensíveis:** pelo menos um teste por serviço captura a saída de log (`OutputCaptureExtension`) de um fluxo completo e verifica a ausência de PAN, CPF e data de nascimento, e um teste de serialização verifica que as mensagens SQS não carregam esses campos. Isso dá rastreabilidade de teste às regras NEVER de log e mensagem, não só às BR-*.

### 5. Testes críticos e rastreabilidade BR-*

- **Onde cada teste crítico mora:** 1 no cardholder-service; 2, 3, 4, 5, 6 e 7 no card-service; 8 no cardholder-service (WireMock para card-service e catálogo). Os testes 9 e 10 atravessam os dois serviços (reconciliação no cardholder → card-service já decidido republica o resultado → cardholder aplica). Opções para a entrevista: (a) dividir cada um em dois ITs, um por serviço, com a mensagem SQS como fronteira e um fixture JSON do envelope versionado em cada módulo; (b) um cenário extra no smoke test contra o Compose; (c) um módulo de testes ponta a ponta. Recomendo (a): mantém `./mvnw verify` como portão e não cria componente novo; (c) contraria o "na dúvida, não construir".
- **Entrega junto com a garantia:** cada teste crítico fica associado à unit/Bolt da garantia que protege (sequência do `scope-document.md`: emissão → 2–7; cadastro → 1, 8, 9, 10), e o incremento não fecha sem ele. Isso casa com `Construction Iteration: unit-major`.
- **Convenção de identificação:** `@DisplayName("CT-03 ...")` e `@Tag("critical")` nos testes críticos; `@Tag("BR-I2")` (uma tag por regra) em todo teste que prova uma BR-*. Com isso, `./mvnw verify -Dgroups=critical` roda só os críticos, e a rastreabilidade pode ser checada mecanicamente.
- **Checagem automática (opção em avaliação):** um passo simples no CI que extrai as 27 regras BR-* do product brief (BR-P1–P3, BR-H1–H6, BR-R1–R5, BR-I1–I5, BR-C1–C5, BR-V1–V3) e falha se alguma não aparecer em nenhuma tag de teste. Alternativa sem código: matriz BR → teste mantida à mão no README ou em `docs/`. A checagem automática é barata e é a única forma de o "cada BR-* rastreável" não degradar até a entrega.
- **Atenção de nomenclatura:** a verificação de rastreabilidade do AI-DLC usa IDs no formato `BR{grupo}.{seq}` (ex.: `BR1.1`), enquanto o brief usa `BR-P1`, `BR-I2`. Functional Design e Build and Test precisam usar um único esquema, ou manter um mapeamento explícito, para que a rastreabilidade não se perca entre os artefatos e as tags dos testes.

### 6. Estratégia de teste e NFRs

- `Test Strategy: Standard` (unitários + integração, 5–8 por componente) é compatível; os testes críticos e obrigatórios são piso adicional, não substituem o volume da estratégia. Testes ponta a ponta ficam restritos ao smoke test.
- Teste de carga está fora do escopo (Q7). Build and Test deve declarar as metas de latência e vazão do brief §7 como "não medidas", nunca como validadas; nenhum portão de qualidade afirma cumprimento de NFR de desempenho.
- Contract testing com Pact fica fora (sem justificativa no brief); o contrato do envelope SQS é coberto pelos fixtures JSON de cada módulo (item 5).

### 7. Lacunas que a entrevista precisa resolver

1. Metodologia: (A) `custom` com testes críticos primeiro, (B) `test-after` com verificação de sabotagem ou (C) `test-after` puro; e a frase exata de `Ordering`.
2. Padrão de exclusão da cobertura (`*Application` + pacote `config`?) e a regra de lista fechada.
3. Confirmação do merge da cobertura de unitários e integração antes do `jacoco:check`; piso de branches: só reportar ou exigir?
4. Smoke test no CI: obrigatório em PR para `main` ou só no push em `main`?
5. Política de testes instáveis: sem retentativa automática (recomendado).
6. Onde vivem os testes críticos 9 e 10, que atravessam serviços: (a), (b) ou (c) do item 5.
7. Convenção de tags (`critical`, `BR-*`) e se a checagem automática de rastreabilidade entra no CI.
8. Esquema único de IDs de regra de negócio (`BR-P1` do brief vs `BR1.1` do framework).
9. Autenticação nos ITs: JWKS via WireMock + `spring-security-test`, com Keycloak real só no smoke test?

## Positions

- AGREE: Piso de 80% de linhas por módulo de serviço, excluindo main e configuração, verificado no `verify` e no CI e nunca rebaixado — é o piso do escopo `mvp` na org e está no núcleo do `scope-document.md`.
- AGREE: `./mvnw verify` como portão único local e no CI, dependendo só de Docker, e CI montado junto com o esqueleto — coerente com `project.md` e com a sugestão do `scope-document.md`.
- AGREE: Rodar `scripts/smoke-test.sh` no CI (U6) em job separado — é o comando de verificação da Construction e deve ser reproduzível fora da máquina local.
- OBJECT: `test-after` puro como metodologia sugerida — não garante que os testes críticos provem comportamento, como exige o `project.md`; recomendo `custom` (críticos primeiro, vistos falhando) ou, no mínimo, `test-after` com verificação de sabotagem registrada.
- OBJECT: "Excluindo as classes de configuração" sem padrão concreto — o JaCoCo exclui por nome de classe, então sem convenção de pacote ou sufixo a exclusão vira brecha para enfraquecer o piso.
- OBJECT: O rascunho não fixa o merge da cobertura de unitários e integração — sem ele, os adaptadores cobertos só por Testcontainers derrubam o percentual e induzem testes artificiais.
- OBJECT: Falta, no rascunho, a regra de não usar `@Transactional` com rollback nos testes críticos e de outbox — esse rollback mascara a semântica de commit/ACK que eles precisam provar.
- AGREE: `discovered-rules.md` sem regras até a entrevista; apoio como candidatas a regra rígida "NEVER fazer merge em `main` com o CI vermelho ou com o piso de cobertura rebaixado" e, adicionalmente, "NEVER configurar retentativa automática de testes com falha no build ou no CI", ambas só se o humano as declarar.
