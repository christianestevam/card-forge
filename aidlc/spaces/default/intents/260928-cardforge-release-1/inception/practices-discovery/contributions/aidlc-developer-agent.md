**Collaborator:** aidlc-developer-agent

## Contribution

> Revisão cega de suporte, foco em convenções de código: nomenclatura, limites
> de camada, tratamento de erros, organização de módulos e pacotes, estilo e
> lint, commits. Repositório greenfield: não há código, `pom.xml`, configuração
> de Spotless/lint nem `.github/workflows/` para inspecionar. Tudo abaixo deriva
> de `project.md`, `engineering-standards.md`, `product-brief.md` e do
> histórico git (2 commits, 1 autor, ambos em Conventional Commits:
> `chore: ...`, `docs(aidlc): ...`). Cada item está classificado conforme a
> regra de `project.md` (Way of Working): **[decidido]** já está em
> `project.md`/`engineering-standards.md`; **[sugestão]** é complemento de
> baixo custo proposto para afirmação; **[em aberto]** a entrevista precisa
> decidir. Nada aqui vira obrigação sem aprovação no gate.

### 1. Nomenclatura (código em inglês, linguagem ubíqua)

A tabela da seção 3 do `product-brief.md` é a fonte da verdade para os
**termos**; o que falta afirmar é a **forma** (caixa e sufixos) de cada tipo de
identificador. Proposta consolidada, para a seção Code Style:

| Elemento | Convenção | Exemplo | Origem |
|---|---|---|---|
| Módulo Maven | kebab-case, sufixo `-service` | `cardholder-service` | [decidido] |
| Pacote base | `com.rpe.cardforge.<contexto>` no singular, sem `-service` | `com.rpe.cardforge.cardholder.application` | [decidido] + [sugestão: mapeamento módulo → segmento] |
| Tipos Java | PascalCase, termo da linguagem ubíqua | `IssuanceRequest`, `RegistrationReceipt` | [decidido] |
| Membros Java | camelCase | `panLastFour`, `validatedAt` | [decidido] |
| Enums | valores em UPPER_SNAKE_CASE, exatamente os do brief | `PENDING`, `NON_CANCELED_CARD_ALREADY_EXISTS` | [decidido] |
| Tabelas e colunas | snake_case; tabelas no plural, exceto as já nomeadas no padrão | `cardholders`, `issuance_processing`, `idempotency_keys`, `pan_hmac` | [decidido] + [sugestão: plural] |
| Migrações Flyway | `V<n>__<verbo>_<objeto>.sql`, uma mudança por arquivo, nunca editadas depois de commitadas | `V1__create_products.sql` | [sugestão] |
| Endpoints | `/api/v1/<recurso-no-plural>`, kebab-case em recursos compostos | `/api/v1/cardholders` | [decidido] |
| Campos JSON | camelCase | `issuanceRequestId` | [decidido] |
| Filas | kebab-case, DLQ com sufixo `-dlq` | `card-issuance-requested-dlq` | [decidido] |
| `eventType` | PascalCase no passado | `CardIssuanceRequested`, `CardIssuanceCompleted` | [em aberto — D1] |
| Métricas | minúsculas separadas por ponto, prefixo `cardforge.` (o Micrometer converte para `_` no Prometheus) | `cardforge.issuance.completed`, `cardforge.outbox.pending` | [em aberto — D1] |
| Chaves Redis | `cardforge:<recurso>:v<n>:{id}` | `cardforge:product:v1:{id}` | [decidido] |
| Propriedades de configuração | prefixo `cardforge.`, kebab-case | `cardforge.catalog.read-timeout` | [sugestão] |

Sufixos de classe por papel (sugestão, para evitar variação entre os três
serviços):

- `web`: `*Controller`; DTOs como records `*Request` / `*Response`; `*ExceptionHandler` para o `@RestControllerAdvice`.
- `application`: casos de uso nomeados pelo verbo de negócio, sem interface obrigatória (ex.: `IssueCard`, `RegisterCardholder`, com um método público `execute`/`handle`) — **[em aberto — D2]**: nome verbal simples vs. sufixo `*UseCase`/`*Service`. Portas: substantivo do papel (`ProductCatalog`, `PanProtector`, `CardRepository`, `EventPublisher`), só em limites reais.
- `infrastructure`: `*Entity` para entidades JPA, `*JpaRepository` para Spring Data, `*Adapter` (ou tecnologia + papel, ex.: `HttpProductCatalog`, `RedisProductCache`) para as implementações de porta, `*Listener` para consumidores SQS.
- `domain`: sem sufixo técnico (`Card`, `Product`, `Cpf`, `Pan`); value objects como records quando imutáveis.

### 2. Limites de camada (hexagonal enxuta)

Direção de dependência a afirmar, por módulo de serviço:

```
web ──► application ──► domain
infrastructure ──► application (portas) e domain
domain ──► (nada além do JDK)
```

- **[decidido]** `domain` sem Spring, JPA, AWS SDK, Jackson, Bean Validation ou qualquer anotação de framework; `@Transactional` só em `application`; entidades JPA separadas, com mapeamento manual; `Clock` injetado.
- **[sugestão]** `web` não acessa `infrastructure` diretamente (nem repositórios Spring Data); controllers chamam casos de uso e traduzem domínio ↔ DTO.
- **[sugestão]** Mapeamento domínio ↔ entidade em métodos estáticos/classes `*Mapper` escritos à mão dentro de `infrastructure`; sem MapStruct (evita processador de anotação e mapeamento implícito de campos sensíveis).
- **[sugestão]** Configuração Spring (`@Configuration`, `@ConfigurationProperties`) em `infrastructure.config` (ou `config` no nível do serviço); o `domain` é instanciado por beans declarados ali, nunca anotado com `@Component`.
- **[em aberto — D3] Verificação automática das camadas.** Hoje a regra "domínio sem frameworks" depende de disciplina. Opções: (a) um teste ArchUnit por serviço com 3 a 4 regras (domain sem `org.springframework..`, `jakarta.persistence..`, `software.amazon..`, `com.fasterxml..`; `web` não depende de `infrastructure`; sem ciclos entre pacotes) — custo de ~1 hora, roda no `./mvnw verify`; (b) só revisão manual. Recomendação: (a), classificada como opção em avaliação; ela transforma uma decisão já aprovada em verificação executável sem criar abstração nova.
- **[em aberto — D4] Código técnico compartilhado entre serviços.** `engineering-standards.md` proíbe módulo de **domínio** compartilhado, mas não diz nada sobre código **técnico** repetido em dois ou três serviços: envelope de evento, worker de outbox (cardholder e card), filtro de `correlationId`, handler de `ProblemDetail`, backoff com jitter. Opções: (a) duplicar em cada serviço (independência total, ~3 cópias pequenas); (b) um módulo `cardforge-platform` só com código técnico, sem nenhum tipo de negócio. Recomendação: (a) para a R1, pelo YAGNI e pela independência de deploy; reavaliar se o outbox divergir entre as cópias. Precisa de decisão explícita porque afeta a estrutura do monorepo desde o esqueleto.
- **[em aberto — D5] Lombok.** Com Java 21, records cobrem DTOs, value objects e eventos. Recomendação: não usar Lombok, o que elimina de vez o risco de `@Data`/`toString()` gerado em tipos com dados pessoais (regra de `engineering-standards.md` § Segurança).

### 3. Tratamento de erros (`ProblemDetail`)

**[decidido]** `@RestControllerAdvice` com `ProblemDetail` (RFC 9457,
`application/problem+json`) e a tabela de códigos de
`engineering-standards.md` § APIs REST. Complementos sugeridos para que os três
serviços respondam de forma idêntica:

- **Exceções de domínio** são tipos específicos e sem framework (ex.: `InvalidStatusTransitionException`, `UnderageCardholderException`), agrupados por uma hierarquia pequena (`sealed` quando fizer sentido). Isso não conflita com a proibição de "classes base genéricas", que mira `BaseService<T>`/`BaseController<T>`.
- **Mapeamento único por serviço**: validação de Bean Validation e JSON malformado → 400 com extensão `errors: [{field, message}]`; `Idempotency-Key` ausente → 400; transição inválida, unicidade e requisição idempotente em andamento → 409; regra de negócio (idade, CPF inválido pelo dígito verificador, produto cancelado no cadastro) e chave reutilizada com payload diferente → 422; recurso inexistente → 404; dependência indisponível sem degradação (circuit aberto, timeout do catálogo no cadastro) → 503; `Throwable` não previsto → 500 genérico, sem mensagem interna.
- **Violação de constraint do banco**: traduzida pelo **nome da constraint** (ex.: `uk_cardholders_cpf` → 409), coerente com o `ON CONFLICT` direcionado; nunca um `DataIntegrityViolationException` genérico virando 409 para qualquer caso. Isso pede nomes explícitos de constraint e índice nas migrações (**[sugestão]**: `uk_<tabela>_<colunas>`, `ix_<tabela>_<colunas>`, `fk_<tabela>_<referência>`).
- **Conteúdo do problema**: `type` estável por categoria (**[em aberto — D6]**: URI própria como `https://cardforge.rpe.com/problems/<slug>` ou `about:blank` com `title` padrão), `title` e `detail` em inglês (a API é código), extensão `correlationId` em toda resposta de erro. `detail` nunca contém CPF, data de nascimento, PAN ou o payload recebido; mensagens de validação citam o campo, não o valor.
- **Fluxo de emissão (assíncrono)**: recusas de negócio (`PRODUCT_NOT_FOUND`, `PRODUCT_CANCELED`, `NON_CANCELED_CARD_ALREADY_EXISTS`) são modeladas como **valor de retorno** do caso de uso (desfecho), não como exceção. Exceções ficam para as outras três classes de falha (transitória, mensagem inválida, configuração), cada uma com tipo próprio na `infrastructure`. Isso torna a regra Forbidden "NEVER classificar erro técnico como falha de negócio" verificável por tipo, e não por inspeção de mensagem.
- Ativar `spring.mvc.problemdetails.enabled=true` para que as exceções do próprio Spring MVC também saiam em `problem+json`.

### 4. Organização de arquivos e módulos

**[decidido]** monorepo, um módulo Maven por serviço, Maven Wrapper,
`docker-compose.yml` na raiz, `docs/adr/`, `postman/`, `scripts/`. Complementos:

- **[sugestão]** `pom.xml` raiz como agregador e pai, com `spring-boot-starter-parent` 3.5.x como parent, `dependencyManagement` para Spring Cloud AWS, Testcontainers e WireMock (BOMs), e `pluginManagement` centralizando Spotless, JaCoCo, Surefire, Failsafe e Compiler. Versões nunca declaradas nos módulos filhos.
- **[sugestão]** Layout por serviço: `src/main/java/com/rpe/cardforge/<contexto>/{domain,application,infrastructure,web}`, `src/main/resources/db/migration`, `src/test/java` espelhando os pacotes; `Dockerfile` dentro do módulo.
- **[sugestão]** Separação unit × integração pelo nome: `*Test` roda no Surefire (fase `test`), `*IT` roda no Failsafe (fase `integration-test`/`verify`). É o que faz `./mvnw verify` rodar os dois tipos e permite `./mvnw test` rápido sem Docker. Os testes críticos de `project.md` ficam como `*IT` com nome que cita o comportamento (ex.: `DuplicateIssuanceMessagesIT`).
- **[sugestão]** `.gitignore` recebe `target/`, `.env` e `*.pem`/`*.key`; versiona-se `.env.example` sem valores reais (reforça a regra Forbidden de não versionar segredos). `.gitattributes` com `*.sh`, `mvnw` e `docker/**` em `eol=lf`, porque `scripts/smoke-test.sh` e o init do LocalStack rodam dentro de containers Linux e no CI.
- **[sugestão]** Configuração por serviço em `application.yml` com perfil `local` para o Compose; nenhum segredo no YAML, só referências a variáveis de ambiente.

### 5. Estilo de código e lint

- **[decidido]** Spotless com google-java-format.
- **[sugestão]** Fixar a versão do google-java-format no `pluginManagement`; `spotless:check` vinculado à fase `verify` (falha o build e, portanto, o CI); `spotless:apply` é o comando local. Escopo inicial: Java e `pom.xml` (`sortPom` opcional). Sem formatação de SQL/YAML para não gerar ruído.
- **Lacuna frente à `org.md`:** a regra da organização diz que um **linter** roda no CI e bloqueia o merge; para Java, nada além do formatador foi definido. **[em aberto — D7]** Opções, do menor para o maior custo:
  - (a) `maven-compiler-plugin` com `-Xlint:all,-processing` e `-parameters` (este último é necessário para o Spring de qualquer forma), sem `-Werror`; custo quase zero;
  - (b) (a) + Error Prone como plugin do compilador, com o conjunto padrão de checks em ERROR; pega bugs reais (ex.: `equals` quebrado, `Optional` mal usado, formatos de string) com baixo ruído; exige `.mvn/jvm.config` com os `--add-exports` do JDK 21;
  - (c) Checkstyle: **não recomendado**, sobrepõe-se quase inteiro ao google-java-format;
  - (d) SpotBugs/PMD: **não recomendados** para a R1 pelo tempo de build e pelo ruído em código Spring.
  - Recomendação: (a) como mínimo afirmado; (b) como opção em avaliação, a decidir pelo humano conforme o prazo. Na ausência de (b), o registro deve dizer explicitamente que "o lint de Java é o conjunto Spotless + avisos do javac", para a regra da organização não ficar sem objeto.
- **[sugestão]** Maven Enforcer com `requireJavaVersion [21,)` e `requireMavenVersion`, e `banCircularDependencies` se houver módulo compartilhado (D4).
- **[sugestão]** Proibir `System.out`/`printStackTrace` e logs em concatenação: SLF4J parametrizado, sem argumento que seja record/DTO com dado pessoal (reforça as duas regras Forbidden de log). Se ArchUnit for adotado (D3), uma regra cobre `System.out`.

### 6. Commits e branches

- **[decidido]** Conventional Commits em inglês, commits pequenos associados à Unit of Work revisada. **[observado]** as duas mensagens atuais já seguem o formato.
- **[sugestão] Tipos e escopos fechados**: tipos `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`; escopos = contexto do serviço (`product`, `cardholder`, `card`) ou área transversal (`infra` para Compose/LocalStack/Keycloak, `ci`, `adr`, `aidlc`, `postman`). Assunto no imperativo, até 72 caracteres, sem ponto final.
- **Compatibilidade squash × commits pequenos:** a regra de `project.md` ("commits pequenos por Unit of Work") vale **na branch do Bolt**; em `main` fica um commit por Bolt (squash, `org.md`). As duas regras são compatíveis e o registro deve dizer isso, para não parecer contradição na checagem de admissão.
- **[sugestão] Referência ao Bolt** no **rodapé** do commit de squash (`Refs: <bolt-slug>`), e não no assunto: mantém o assunto curto e parseável por ferramentas de changelog.
- **[sugestão]** Branches curtas `<tipo>/<bolt-slug>` (ex.: `feat/walking-skeleton`); nenhuma mensagem de commit contém dado pessoal, mesmo de teste (CPF de fixture).
- **[em aberto — D8]** Commits gerados com assistência de IA carregam o trailer `Co-Authored-By`? (Decisão de transparência do autor; não afeta o formato do assunto.)

### 7. Lacunas que a entrevista precisa resolver

| ID | Pergunta | Recomendação |
|---|---|---|
| D1 | Formato de `eventType` e de nomes de métricas | `eventType` PascalCase no passado; métricas `cardforge.<área>.<medida>` |
| D2 | Nome das classes de caso de uso | Verbo de negócio sem sufixo (`IssueCard`), sem interface |
| D3 | Verificação automática das camadas | ArchUnit mínimo por serviço (opção em avaliação) |
| D4 | Código técnico compartilhado (outbox, envelope, correlação, ProblemDetail) | Duplicar na R1; sem módulo comum |
| D5 | Lombok | Não usar; records |
| D6 | `type` do ProblemDetail | URI estável por categoria, ou `about:blank` se o humano preferir o mínimo |
| D7 | Lint além do formatador | javac `-Xlint` como mínimo; Error Prone opcional |
| D8 | Trailer de coautoria de IA nos commits | Decisão do autor |

Candidata a regra rígida (só entra em `discovered-rules.md` se o humano a
declarar como restrição, pois hoje é decisão de estilo em `project.md`):
`NEVER importar Spring, JPA, AWS SDK ou Jackson em pacotes domain`.

## Positions

- AGREE: Trunk-based em `main` com aposentadoria do `develop` (U1, opção a) — é a política da org, e o único uso de `develop` hoje é um commit ainda não enviado.
- AGREE: Code Style remetendo a `project.md` para pacote base, DTOs, `ProblemDetail` e enums, sem repetir — evita duas fontes para a mesma regra.
- OBJECT: Code Style diz "seguimos a configuração de formatação e lint versionada", mas não existe lint definido para Java e a `org.md` exige um linter bloqueante no CI; o texto afirmado precisa nomear o gate concreto (Spotless + javac `-Xlint`, e Error Prone se aprovado — D7).
- OBJECT: Colocar `[bolt-<slug>]` no assunto do commit de squash — prefiro o rodapé `Refs: <bolt-slug>`, que mantém o assunto curto e compatível com ferramentas de Conventional Commits.
- AGREE: Merge em `main` condicionado ao CI verde, desde que o CI nasça junto com o esqueleto (U6); antes disso não há gate e a regra ficaria sem efeito para os primeiros commits.
- AGREE: Nomenclatura idiomática com snake_case em SQL — complementada pela tabela da seção 1 (filas, eventos, métricas, migrações, constraints), que o rascunho ainda não cobre.
- AGREE: `discovered-rules.md` sem regras novas no rascunho; a única candidata da minha área (domínio sem frameworks) depende de declaração do humano.
