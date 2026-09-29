**Collaborator:** aidlc-devsecops-agent

## Contribution

Revisão cega do rascunho do lead (`team-practices.md`, `discovered-rules.md`, `evidence.md`) sob a ótica de segurança da cadeia de entrega. Foco: formatação e lint, SAST, varredura de segredos, dependências e imagens, controles de supply chain e, sobretudo, como as chaves do PAN e do HMAC ficam fora do repositório no ambiente Docker Compose. Tudo aqui é **sugestão classificada** (conforme `project.md` › Way of Working): nada vira obrigação sem a entrevista e o gate de afirmação. O critério foi proporcionalidade: uma pessoa, meio período, entrega em 2026-10-05. Os controles são de aplicação e de pipeline; nada aqui afirma conformidade integral com PCI-DSS ou LGPD (C-R1, `project.md` › Corrections).

### 1. Evidência adicional (complementa `evidence.md`)

| Fonte | O que mostrou | Implicação de segurança |
|---|---|---|
| `git ls-files` (fora de `aidlc/` e `.claude/`) | Apenas `.gitignore`. Nenhum segredo versionado até agora; o histórico está limpo | Momento ideal para travar a entrada de segredos: o primeiro commit com `docker-compose.yml`, realm do Keycloak ou `application.yml` já é o ponto de risco |
| `git check-ignore .env` | `.env` **não** é ignorado; só `.env.local` (pelo padrão genérico `*.local`) | Hoje, um `.env` com chaves de PAN/HMAC seria commitado por engano com `git add .` |
| `.gitignore` | Sem `target/`, `.env`, `*.pem`, `*.key`, `*.p12`, arquivos de ambiente do Postman | Adicionar antes de qualquer chave ou credencial existir no disco |
| `engineering-standards.md` › Segurança | AES-256-GCM com versão da chave; HMAC com chave separada; "chave ausente impede a inicialização"; produção via Secrets Manager/KMS | O mecanismo local de entrega das chaves não está definido em nenhum artefato |
| `engineering-standards.md` › Idempotência | Fingerprint = HMAC do payload canônico (payload contém CPF e data de nascimento) | Existe uma **terceira chave** (no cardholder-service), não citada em nenhum artefato até agora |
| `project.md` › Deployment | "Keycloak com realm importado de arquivo versionado (clients, escopos, audiência)" | Um realm exportado contém o `secret` dos clients confidenciais; versioná-lo como está conflita com `NEVER versionar credenciais, segredos ou chaves` |
| `project.md` › Tech Stack | Spring Boot 3.5.x fora do suporte OSS desde junho de 2026 | Scanners de dependência vão acusar CVEs cuja correção só existe na linha 4.x; sem processo de exceção, o CI trava ou o time desliga o gate |
| Ausência de `.github/` | Sem CI, sem Dependabot, sem proteção de branch | Todos os gates abaixo nascem do zero; nenhum custo de migração |

### 2. Chaves e credenciais no ambiente local (a lacuna mais importante)

**Inventário de segredos da R1** (nenhum pode ser versionado; `project.md` › Forbidden):

| Segredo | Quem usa | Sensibilidade |
|---|---|---|
| Chave AES-256-GCM do PAN (+ versão) | só `card-service` | Crítica |
| Chave HMAC do PAN (`pan_hmac`) | só `card-service` | **Crítica, igual à de cifragem**: o PAN tem só 7 dígitos aleatórios (10^7 candidatos por BIN conhecido); com a chave HMAC vazada, o PAN é recuperado por força bruta em segundos a partir de `pan_hmac` |
| Chave HMAC do fingerprint de idempotência | só `cardholder-service` | Alta: CPF e data de nascimento têm pouca entropia; fingerprint com chave vazada permite confirmar CPF por força bruta |
| Secrets dos clients confidenciais do Keycloak (gateway/Postman, `cardholder-service` e `card-service` via client credentials) | serviços, smoke test, Postman | Média (local) |
| Admin do Keycloak, senhas do PostgreSQL | infraestrutura local | Baixa (local), mas são credenciais pela letra da regra |
| Credenciais do LocalStack (`test`/`test`) | serviços | Valor fictício público; não dá acesso a nada |

**Tensão a resolver:** `project.md` exige "ambiente completo com um único comando" (`docker compose up -d --build`) e, ao mesmo tempo, `NEVER versionar credenciais, segredos ou chaves`. Sem um mecanismo definido, a saída "rápida" sob prazo é commitar um `.env` ou um realm com secrets.

**Opções (para a entrevista):**

- **A. Serviço de init no Compose (recomendado).** Um container de execução única (`key-init`, imagem mínima com `openssl`) gera, **só se ainda não existirem**, todas as chaves e secrets locais em um volume nomeado. Os serviços montam o volume em modo somente leitura e leem os valores via `spring.config.import=optional:configtree:/run/secrets/` (suporte nativo do Spring Boot a secrets montados como arquivos, o mesmo modelo de secrets do ECS/Kubernetes em produção). Os serviços declaram `depends_on: key-init: condition: service_completed_successfully`. Mantém o comando único, não põe nenhum segredo no repositório nem em variáveis de ambiente visíveis via `docker inspect`, e o CI não precisa de nenhum GitHub secret. O Keycloak recebe os secrets dos clients por placeholders `${...}` no realm versionado (substituição suportada no `--import-realm`), exportados a partir do volume por um entrypoint curto. Custo: ~2 a 3 horas no esqueleto.
- **B. Script de preparação.** `scripts/setup-local-env.sh` gera um `.env` ignorado com `openssl rand -base64 32`; o Compose usa `${VAR:?mensagem}` para falhar se faltar. Mais simples, mas vira dois comandos e as chaves ficam em variáveis de ambiente (visíveis em `docker inspect` e `docker compose config`).
- **C. Valores fixos de desenvolvimento versionados.** Rejeitada para as chaves criptográficas (viola `NEVER versionar ... chaves`). Para credenciais de infraestrutura local, só com exceção explícita declarada pelo humano.

**Controles associados (independentes da opção escolhida):**

- `.gitignore`: `.env`, `.env.*` (exceto `.env.example`), `*.pem`, `*.key`, `*.p12`, `*.jks`, `target/`, `postman/*.local.postman_environment.json`. Adicionar no primeiro commit do esqueleto.
- `.dockerignore` em cada módulo/raiz: `.git`, `.env*`, `target/`, `aidlc/`, `.claude/`. Nunca `ENV`/`ARG` com segredo em Dockerfile.
- Nenhum valor default para chave ou secret em `application.yml` (ex.: `${CARDFORGE_PAN_HMAC_KEY}` sem `:`), no Dockerfile ou no Compose.
- Validação no startup (`@ConfigurationProperties` + `@Validated`, já previsto em `engineering-standards.md`): chave ausente, com tamanho diferente de 32 bytes, ou **igual a outra chave** impede a inicialização. As três chaves são obrigatoriamente distintas.
- Menor privilégio no Compose: as chaves do PAN chegam só ao `card-service`; a chave de fingerprint só ao `cardholder-service`; o `product-service` não recebe nenhuma.
- Actuator exposto apenas com `health`, `info` e `prometheus`; nunca `env`, `configprops`, `heapdump` ou `loggers` (vazariam chaves ou dados pessoais).
- Portas de PostgreSQL, Redis, LocalStack e Keycloak publicadas só em `127.0.0.1` (credenciais locais fracas não ficam expostas na rede do notebook).
- Testes: Testcontainers e unitários geram chaves aleatórias em tempo de execução (`KeyGenerator`/`SecureRandom`); nenhuma chave literal em código de teste (evita ambiguidade com a regra e ruído no scanner de segredos).
- Postman: a collection usa variáveis (`{{clientSecret}}`); um environment template versionado com valores vazios; o environment preenchido fica local e ignorado. O smoke test e o Postman obtêm o secret local por um script (ex.: `scripts/print-local-credentials.sh`) que lê o volume da opção A.
- Ciclo de vida: `docker compose down -v` descarta chaves e dados juntos, o que é coerente (PAN cifrado sem chave seria ilegível). Remover só o volume de chaves com o banco preservado torna os PANs ilegíveis e colide o `pan_hmac`; documentar no README. Rotação continua como débito (R7, brief §9).

### 3. Gates de segurança no pipeline (proporcionais)

Princípio: `./mvnw verify` continua sendo o portão local, dependente só de Docker e sem chamadas a serviços externos; os scanners que precisam de base de vulnerabilidades online rodam como jobs separados no GitHub Actions.

| Controle | Ferramenta sugerida | Onde | Critério de bloqueio | Custo | Classificação |
|---|---|---|---|---|---|
| Formatação | Spotless (google-java-format), `spotless:check` no `verify` | local + CI | qualquer desvio | já decidido | decisão aprovada |
| Lint + SAST de código Java | SpotBugs + plugin **FindSecBugs**, no `verify`, só as categorias de segurança e correção, prioridade alta | local + CI | achado de prioridade alta | ~1 h | opção em avaliação (responde também a U8 do lead) |
| SAST gerenciado | CodeQL (GitHub) | CI | High/Critical | ~15 min **se o repositório for público**; em repositório privado exige licença paga | opção em avaliação (depende da visibilidade) |
| Segredos | Trivy `fs` com scanner de segredos, ou Gitleaks CLI; mais checagem trivial de que nenhum `.env` está rastreado (`git ls-files`) | CI em todo push/PR | qualquer achado | ~30 min | recomendado |
| Segredos (antes do commit) | hook de pre-commit do Gitleaks | local | bloqueia o commit | ~15 min | opcional |
| Dependências (CVE) | Trivy `fs` sobre os `pom.xml` + **Dependabot** (alertas e PRs semanais para `maven`, `docker` e `github-actions`) | CI + GitHub | CRITICAL com correção disponível | ~30 min | recomendado |
| Imagens | Trivy `image` sobre as 3 imagens construídas | CI | CRITICAL com correção disponível; HIGH só reportado | ~30 min | recomendado |
| Dockerfile/Compose | Trivy `config` | CI | apenas relatório | ~0 (mesma ferramenta) | opcional |
| SBOM | CycloneDX Maven plugin | CI | nenhum | ~15 min | fora da release (evolução) |
| DAST | OWASP ZAP | — | — | alto | fora da release: sem ambiente remoto; coberto em parte pelos testes de autenticação abaixo |
| IaC (cfn-nag, Checkov) | — | — | — | — | não se aplica: sem infraestrutura AWS nesta iniciativa |

**Por que não OWASP dependency-check:** exige chave da API do NVD (um segredo a mais no CI), é lento (download da base) e instável com os atrasos de enriquecimento do NVD. Trivy + Dependabot cobre dependências, imagens e segredos com uma única ferramenta e sem segredo no CI.

**Exceções (waivers):** um único arquivo versionado (`.trivyignore` ou equivalente) em que cada entrada tem CVE, justificativa, responsável e data de validade; entrada vencida volta a bloquear. Isso é necessário desde o primeiro dia por causa do Spring Boot 3.5 fora do suporte OSS: CVEs corrigidos só na linha 4.x precisam de waiver ligado ao ADR de migração, e não de um gate desligado.

**Testes de segurança baratos (sugestão para o quality-agent integrar):**

- Autenticação na borda, por serviço: sem token → 401; escopo errado → 403; audiência ou emissor errados → 401. Prova o brief §6 por comportamento.
- Não vazamento: nos testes críticos de integração, capturar a saída de log e verificar que o CPF, a data de nascimento e o PAN usados no teste não aparecem; e que as mensagens SQS publicadas não contêm esses valores. Prova três regras `NEVER` já afirmadas sem ferramenta nova.
- `toString()` dos tipos sensíveis não contém o valor.
- API nunca retorna PAN completo (só `panLastFour`) e CPF sai mascarado.

### 4. Supply chain

- **Maven Wrapper:** gerar com `distributionSha256Sum` (e `wrapperSha256Sum`) em `.mvn/wrapper/maven-wrapper.properties`, para que o `./mvnw` recuse uma distribuição adulterada. Custo: minutos.
- **Plugins Maven:** versões explícitas (maven-enforcer com `requirePluginVersions`), já que o Boot gerencia dependências, mas não todos os plugins.
- **Imagens:** nunca `latest`. Base das aplicações `eclipse-temurin:21-jre` (JRE, não JDK) com tag específica, idealmente fixada por digest; infraestrutura do Compose (PostgreSQL, Redis, Keycloak, LocalStack) com tag de versão específica. Dependabot (`docker`) mantém os digests atualizados. Risco a verificar no esqueleto: confirmar que a versão fixada do LocalStack sobe sem token de conta, porque a política de distribuição da imagem mudou recentemente; fixar a versão evita quebra silenciosa no dia da entrega.
- **GitHub Actions:** ações fixadas por SHA de commit (atualizadas pelo Dependabot `github-actions`), sobretudo as de scanners de segurança, que já foram alvo de sequestro de tags; `permissions: contents: read` no workflow (mais `security-events: write` só no job que publica SARIF); gatilhos `push` e `pull_request`, nunca `pull_request_target`.
- **Proteção de branch em `main`:** status checks obrigatórios incluindo os jobs de segurança; sem force push. (Complementa U3 do lead; com uma única pessoa, o gate automático substitui a segunda revisão.)

### 5. Prioridade sugerida dentro do prazo

1. **No primeiro commit do esqueleto (antes de existir qualquer chave):** `.gitignore`/`.dockerignore`, mecanismo de chaves (opção A ou B), sem defaults, validação de startup, Actuator restrito, portas em `127.0.0.1`, imagens com versão fixa, checksum do Maven Wrapper.
2. **Junto com o CI (sugestão do scope-document: nascer com o esqueleto):** job Trivy (segredos, dependências, imagens), Dependabot, ações por SHA, permissões mínimas, proteção de branch.
3. **Com as garantias:** testes de autenticação e de não vazamento; SpotBugs + FindSecBugs se escolhido.
4. **Fora da release:** SBOM, DAST, rotação automatizada, CodeQL em repositório privado.

Custo total estimado dos itens 1 e 2: cerca de meio dia a um dia.

### 6. Incertezas que a entrevista precisa resolver

- **S1 — Mecanismo de chaves locais:** opção A (init + volume + configtree, um comando), B (script + `.env`) ou outra?
- **S2 — Credenciais locais de infraestrutura:** senhas do PostgreSQL, admin do Keycloak, secrets dos clients do Keycloak e `test`/`test` do LocalStack também são geradas (leitura literal de `NEVER versionar credenciais`) ou algumas viram exceção declarada? Recomendação: gerar tudo, com exceção explícita apenas para as credenciais fictícias do LocalStack.
- **S3 — Visibilidade do repositório no GitHub:** público ou privado? Define se CodeQL, secret scanning e push protection nativos do GitHub estão disponíveis sem custo.
- **S4 — SAST:** SpotBugs + FindSecBugs no `verify`, CodeQL, os dois ou nenhum nesta release?
- **S5 — Severidade que bloqueia:** CRITICAL com correção disponível bloqueia e HIGH só reporta? Qualquer segredo detectado bloqueia?
- **S6 — Processo de waiver:** aceitar um arquivo único de exceções com justificativa e validade, vinculado ao ADR do Spring Boot 3.5 → 4.x?
- **S7 — Chaves em testes:** confirmar que testes geram chaves em tempo de execução e que nenhuma chave literal entra em código de teste.
- **S8 — Pre-commit local:** instalar o hook do Gitleaks ou confiar só no CI?

### 7. Candidatas a regra rígida (só entram em `discovered-rules.md` se o humano declarar)

Verificadas contra `project.md` › Mandated/Forbidden: nenhuma duplica regra já afirmada; as três primeiras detalham como cumprir `NEVER versionar credenciais, segredos ou chaves`.

- `ALWAYS` impedir a inicialização quando uma chave de cifragem do PAN, HMAC do PAN ou fingerprint de idempotência estiver ausente, malformada ou igual a outra dessas chaves.
- `NEVER` usar a mesma chave para cifragem do PAN, HMAC do PAN e fingerprint de idempotência.
- `NEVER` definir valor default para chaves ou segredos em arquivos de configuração, Dockerfile ou Compose.
- `NEVER` expor pelo Actuator endpoints além de `health`, `info` e `prometheus`.
- `NEVER` fazer merge em `main` com segredo detectado ou vulnerabilidade CRITICAL com correção disponível sem waiver registrado com justificativa e validade.
- `NEVER` usar imagens Docker com tag `latest` ou sem versão fixada.

## Positions

- AGREE: Trunk-based em `main` com merge condicionado ao CI verde; basta incluir os jobs de segurança entre os status checks obrigatórios.
- AGREE: `./mvnw verify` como portão único local, dependente só de Docker; os scanners online (Trivy, Dependabot) devem ficar como jobs separados do CI para não quebrar essa propriedade.
- AGREE: Spotless com `spotless:check` no `verify`; U8 (lint além da formatação) pode ser resolvida junto com SAST por SpotBugs + FindSecBugs em um único plugin.
- AGREE: Rollback local com `docker compose down -v` + `up -d --build`, desde que as chaves geradas vivam no mesmo ciclo de vida dos dados (volume descartado junto).
- OBJECT: `evidence.md` não registra que `.env` não está no `.gitignore` nem a tensão entre "um único comando" e `NEVER versionar credenciais, segredos ou chaves`; faltam as incertezas S1 a S8, sem as quais o primeiro commit do esqueleto pode versionar chaves.
- OBJECT: `discovered-rules.md` conclui que as regras já afirmadas "cobrem as restrições conhecidas", mas o realm versionado do Keycloak (`project.md` › Deployment) contém secrets de clients e conflita com a regra de não versionar credenciais; o conflito precisa ser resolvido na entrevista, não presumido como resolvido.
- OBJECT: Nenhum artefato menciona a terceira chave (HMAC do fingerprint de idempotência no cardholder-service) nem que a chave HMAC do PAN tem a mesma sensibilidade da chave de cifragem (baixa entropia do PAN); ambas devem constar do inventário de segredos.
- OBJECT: O rascunho do CI (Deployment, U6) não prevê processo de exceção para CVEs; com o Spring Boot 3.5 fora do suporte OSS, sem waiver com validade o gate de dependências ou trava a entrega ou acaba desligado.
