# Evidências — Practices Discovery

> Lead: `aidlc-pipeline-deploy-agent`. Apoio: `aidlc-quality-agent`,
> `aidlc-developer-agent`, `aidlc-devsecops-agent` (revisões cegas em
> `contributions/`). Projeto greenfield (`Project Type: Greenfield`, `Scope: mvp`),
> sem artefatos de reverse-engineering; os insumos brownfield do estágio não se
> aplicam. As respostas da entrevista (`practices-discovery-questions.md`,
> Q1–Q12, resumo confirmado com "Looks correct") são a fonte autoritativa:
> sugestões dos participantes que o humano não escolheu não viraram prática nem
> regra.

## O que cada participante inspecionou ou inferiu

### Lead (`aidlc-pipeline-deploy-agent`)

| Fonte | O que mostrou |
|---|---|
| `git branch -a`, `git log --all` | Branches `main` e `develop` (HEAD). `main` = `origin/main` = `origin/develop` = `1ec1c44`; `develop` local 1 commit à frente (`d873fed`), não enviado. Um único autor; as duas mensagens já seguem Conventional Commits. |
| Arquivos versionados fora de `.claude/` | Só o workspace `aidlc/` e `.gitignore`. Nenhum código, `pom.xml`, `docker-compose.yml`, `.github/workflows/`, `scripts/` ou configuração de Spotless/JaCoCo. |
| `.gitignore` | Só o bloco padrão do AI-DLC; nada de Java/Maven (ex.: `target/`). |
| `org.md`, `team.md`, `project.md` | `org.md`: trunk-based em `main`, squash por Bolt, test-after sem postura afirmada + piso de 80% no `mvp`, staging no merge e produção com aprovação manual. `team.md`: template vazio (primeira execução). `project.md`: seções já populadas, `Mode: strict`, 14 Forbidden, 8 Mandated, 4 Corrections. |
| `.claude/scopes/aidlc-mvp.md`, `aidlc-state.md` | `skeleton: on`; `Construction Checkpoints: enabled`, `Construction Iteration: unit-major`, `Test Strategy: Standard`. |
| `engineering-standards.md`, `scope-document.md` | `./mvnw verify` com Testcontainers e WireMock, JaCoCo, Spotless, GitHub Actions; esqueleto primeiro e depois risco primeiro; cada garantia entregue com os seus testes críticos; CI sugerido junto com o esqueleto. |

Inferências: trunk em `main` é o encaixe natural (o uso de `develop` parecia hábito, sem fluxo de release); a cerimônia do esqueleto se aplica; não há ambiente remoto nesta release; o lint além da formatação não estava definido.

### Quality (`aidlc-quality-agent`)

- Inspecionou a postura de testes do rascunho contra `org.md`, `project.md`, `engineering-standards.md`, `product-brief.md` §5/§7 e `scope-document.md`; não havia `pom.xml`, JaCoCo nem CI para observar.
- Inferiu que um teste crítico escrito depois e nunca visto falhando não prova comportamento, e recomendou `custom` (críticos primeiro). Apontou que o piso de 80% exige combinar a cobertura de unitários e integração e exclusões concretas (o JaCoCo exclui por nome, não por anotação), e recomendou não haver retentativa automática de testes.
- Levantou pontos de implementação (sem `@Transactional` com rollback em testes críticos, `Clock` mutável, Awaitility, `CyclicBarrier`, tags `critical`/`BR-*`, onde vivem os testes 9 e 10, JWKS via WireMock) e a divergência de esquema de IDs de regra (`BR-P1` do brief vs `BR1.1` do framework).

### Developer (`aidlc-developer-agent`)

- Inspecionou `project.md`, `engineering-standards.md`, `product-brief.md` e o histórico git; não havia código nem configuração de lint.
- Propôs uma tabela de nomenclatura, sufixos por papel, direção de dependência entre camadas, mapeamento de erros para `ProblemDetail`, layout Maven e convenções de commit, classificando cada item como decidido, sugestão ou em aberto (D1–D8).
- Apontou que a `org.md` pede um linter bloqueante e nada além do formatador estava definido; sugeriu ArchUnit por serviço; mostrou que "commits pequenos" (`project.md`) e squash por Bolt (`org.md`) são compatíveis; preferia o slug do Bolt no rodapé do commit.

### DevSecOps (`aidlc-devsecops-agent`)

- Inspecionou `git ls-files`, `.gitignore` (`git check-ignore .env`: `.env` não é ignorado), `engineering-standards.md` › Segurança e Idempotência, `project.md` › Deployment e Tech Stack, e a ausência de `.github/`.
- Inferiu a tensão entre "um único comando" e `NEVER versionar credenciais, segredos ou chaves`; identificou a terceira chave (HMAC do fingerprint de idempotência) e que a chave HMAC do PAN tem sensibilidade igual à de cifragem; apontou que um realm exportado do Keycloak contém secrets de clients; e que o Spring Boot 3.5 fora do suporte OSS exige um processo de exceção para CVEs.
- Propôs gates proporcionais (Trivy, Dependabot, SpotBugs + FindSecBugs), controles de supply chain e candidatas a regra rígida.

## Decisões da entrevista (Q1–Q12)

| Pergunta | Decisão |
|---|---|
| Q1 — Branches | Trunk em `main`; o commit de `develop` vai para `main` e o `develop` é aposentado. |
| Q2 — Entrada em `main` | Merge local em `main` depois de o CI ficar verde no branch de trabalho. |
| Q3 — Referência ao Bolt | No assunto do commit de squash: `feat(card): issue card with product validation [bolt-<slug>]`. |
| Q4 — Walking skeleton | Sim; nada além dele é construído até o smoke test passar e o humano aprovar. |
| Q5 — Ordem de testes | Misto (`custom`): teste crítico de cada garantia escrito antes e visto falhando pelo motivo certo; demais testes depois de cada camada. |
| Q6 — Cobertura | 80% de linhas por módulo somando unitários e integração; exclusões fechadas em `*Application` e no pacote `config`; branches só reportadas. |
| Q7 — CI | `./mvnw verify` (unitários, integração, cobertura, formatação) e job separado do smoke test contra o Compose; nasce junto com o esqueleto. |
| Q8 — Entrega | Sem deploy remoto; tag `v1.0.0` em `main`; rollback = tag anterior + `docker compose down -v` e subir de novo. |
| Q9 — Verificação de código | SpotBugs com FindSecBugs (segurança e correção, prioridade alta) e um teste ArchUnit por serviço para o domínio. |
| Q10 — Segredos locais | Container de inicialização gera, na primeira execução, em `./.local/secrets` (ignorado pelo git) e montado nos serviços: chaves do PAN (cifragem e HMAC), chave HMAC da idempotência, senhas do banco, secrets dos clients do Keycloak (renderizados no realm importado) e um environment do Postman preenchido; exceção só para `test/test` do LocalStack. |
| Q11 — Segurança no CI | Trivy (segredos, dependências, imagens; bloqueia segredo e CRITICAL com correção) e Dependabot semanal, agrupado, ignorando saltos major do Spring Boot; exceções do Trivy só com justificativa e validade, ligadas ao ADR do Spring Boot 3.5. |
| Q12 — Regras rígidas | Uma `ALWAYS` e sete `NEVER` (ver `discovered-rules.md`); a opção D foi substituída pela redação do humano com a exceção do LocalStack. |

## Como as objeções foram resolvidas

- **Quality — `test-after` puro não prova comportamento:** resolvida por Q5 (`custom`, crítico primeiro e visto falhando).
- **Quality — exclusões de cobertura sem padrão e sem combinação de unitários + integração:** resolvida por Q6 (`*Application` + pacote `config`, lista fechada, cobertura combinada antes da verificação).
- **Quality — sem `@Transactional` com rollback nos testes críticos:** não perguntada nem afirmada; fica como sugestão para Functional Design/Code Generation, sem virar obrigação.
- **Developer — lint bloqueante inexistente frente à `org.md`:** resolvida por Q9 (SpotBugs + FindSecBugs bloqueante; ArchUnit por serviço). As opções `-Xlint` e Error Prone não foram escolhidas.
- **Developer — slug do Bolt no rodapé:** o humano escolheu o assunto (Q3); a objeção foi vencida.
- **Developer — compatibilidade entre commits pequenos e squash:** registrada explicitamente em `team-practices.md` (`## Way of Working`).
- **DevSecOps — `.env` não ignorado e tensão comando único × segredos:** resolvida por Q10 (container de inicialização e `./.local/secrets`); o ajuste do `.gitignore` fica como ação do primeiro commit do esqueleto (ver incertezas).
- **DevSecOps — realm do Keycloak versionado com secrets:** resolvida por Q10 (secrets renderizados no realm importado a partir de `./.local/secrets`).
- **DevSecOps — terceira chave e sensibilidade da chave HMAC do PAN:** incorporadas a Q10 e à regra rígida de chaves distintas e validadas na inicialização (Q12).
- **DevSecOps — sem processo de exceção para CVEs:** resolvida por Q11 (exceções do Trivy com justificativa e validade, ligadas ao ADR do Spring Boot 3.5).
- **Sugestões não escolhidas e portanto não afirmadas:** proteção de branch no GitHub e pull request obrigatório (Q2 escolheu merge local), CodeQL, Gitleaks em pre-commit, SBOM, fixação de actions por SHA, portas em `127.0.0.1`, checksum do Maven Wrapper, tabela de nomenclatura e sufixos por papel do developer (D1, D2, D4–D8), convenção de tags de teste e checagem automática de rastreabilidade BR-*. Permanecem como opções em avaliação para as etapas seguintes.

## Incertezas remanescentes

- **Esquema de IDs de regra de negócio:** o brief usa `BR-P1`, `BR-I2`; a rastreabilidade do AI-DLC espera `BR{grupo}.{seq}` (ex.: `BR1.1`). Decidir em Functional Design um único esquema ou um mapeamento explícito.
- **`.gitignore`:** hoje não ignora `.env`, `.local/` nem `target/`; os três devem entrar no primeiro commit do esqueleto, antes de qualquer chave existir no disco.
- **Versão da imagem do LocalStack:** fixar uma versão (regra de imagens sem `latest`) e verificar no esqueleto que ela sobe sem token de conta.
- **Realm do Keycloak:** a exigência de `project.md` de "realm importado de arquivo versionado" é atendida por um template de realm versionado com placeholders, com os secrets renderizados a partir de `./.local/secrets`; nenhum secret é versionado.
- **Nota (fora das cinco seções):** o escopo `mvp` sugere `guard_policy: relaxed`, mas `project.md` afirma `Mode: strict`, que prevalece.
