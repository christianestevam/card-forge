# Descoberta de Práticas: entrevista

O engenheiro de release fez um rascunho das práticas do time (`team-practices.md`). O engenheiro de qualidade, o desenvolvedor e o engenheiro de segurança revisaram esse rascunho de forma independente (arquivos em `contributions/`). As perguntas abaixo cobrem só o que o rascunho e as revisões não conseguiram estabelecer. As sugestões vêm dos padrões da organização (`org.md`) e das regras do projeto (`project.md`).

Escolha uma letra (ou várias, onde indicado) na linha de resposta de cada pergunta.

## Q1. Como os branches funcionam?

Contexto: o padrão da organização é desenvolvimento baseado em trunk, isto é, um único branch principal (`main`) e branches de trabalho curtos que voltam para ele em 1 a 2 dias. Hoje o repositório está no branch `develop`, um commit à frente de `main`.

A. Trunk em `main`: levar o commit de `develop` para `main` e aposentar o `develop`
B. Manter `develop` como branch de integração (contraria a política da organização; exigiria mudar `org.md`)
C. Not yet defined
X. Other (please specify)

[Answer]: A. Trunk em `main`: levar o commit de `develop` para `main` e aposentar o `develop` **Mode:** guided

## Q2. Como o trabalho entra em `main`?

Contexto: com uma pessoa só, não há segundo revisor humano; os gates do workflow e o CI fazem esse papel.

A. Pull request para `main`, com CI obrigatório e proteção de branch (sem push direto nem force push), e squash-merge
B. Merge local em `main` depois de o CI ficar verde no branch de trabalho
C. Push direto em `main`, com o CI rodando depois
D. Not yet defined
X. Other (please specify)

[Answer]: B. Merge local em `main` depois de o CI ficar verde no branch de trabalho **Mode:** guided

## Q3. Onde o commit que entra em `main` cita a unidade de trabalho (o Bolt)?

Contexto: o padrão é um commit por Bolt, por squash, em Conventional Commits em inglês. O rascunho sugeria o slug no assunto; o desenvolvedor prefere o rodapé, para não poluir o assunto.

A. No assunto: `feat(card): issue card with product validation [bolt-<slug>]`
B. No rodapé: `Refs: <bolt-slug>`
C. Sem referência ao Bolt
X. Other (please specify)

[Answer]: A. No assunto: `feat(card): issue card with product validation [bolt-<slug>]` **Mode:** guided

## Q4. Construir primeiro uma fatia fina que funciona de ponta a ponta?

Contexto: um *walking skeleton* é uma versão mínima que percorre o sistema inteiro, construída antes das funcionalidades reais para provar que as peças se conectam. Em `project.md` ele já está definido (token, produto, cadastro, emissão pela fila com o cache, consulta consolidada), com a verificação feita por `scripts/smoke-test.sh`. Com "sim", nada além dele é construído até ele passar e você aprová-lo.

A. Sim
B. Não
X. Other (please specify)

[Answer]: A. Sim **Mode:** guided

## Q5. Em que ordem testes e código são escritos?

Contexto: a regra do projeto é que cada teste crítico prove um comportamento, não apenas execute o código. O engenheiro de qualidade alerta que um teste escrito depois e que nunca foi visto falhando não dá essa prova.

A. Teste depois: implementar cada camada e em seguida escrever e rodar os testes dela; nenhuma garantia fecha sem os seus testes críticos verdes
B. Misto: o teste crítico de cada garantia é escrito antes da implementação e visto falhando; os demais testes unitários e de integração vêm depois de cada camada
C. Teste primeiro em todo o código (TDD)
D. Cenários de negócio primeiro (BDD ou ATDD)
X. Other (please specify)

[Answer]: B. Misto: o teste crítico de cada garantia é escrito antes e visto falhando pelo motivo certo (a asserção do comportamento, não erro de compilação ou de setup); os demais testes são escritos depois de cada camada. **Mode:** guided

## Q6. Como a cobertura mínima de 80% de linhas por módulo é medida?

A. Somando testes unitários e de integração antes de verificar o piso; exclusões fechadas na classe `*Application` e no pacote `config`; cobertura de branches só reportada, sem piso próprio
B. Só com testes unitários
C. Como em A, mas com piso também para branches (especifique em X)
D. Not yet defined
X. Other (please specify)

[Answer]: A. Somando testes unitários e de integração antes de verificar o piso; exclusões fechadas na classe `*Application` e no pacote `config`; cobertura de branches só reportada, sem piso próprio **Mode:** guided

## Q7. O que o CI executa e quando ele nasce?

A. `./mvnw verify` (unitários, integração, piso de cobertura e checagem de formatação) e um job separado com o `scripts/smoke-test.sh` contra o Docker Compose; o CI nasce junto com o esqueleto
B. Só `./mvnw verify` no CI; o smoke test roda apenas localmente; o CI nasce junto com o esqueleto
C. Como em A, mas o CI nasce depois do esqueleto
D. Not yet defined
X. Other (please specify)

[Answer]: A. `./mvnw verify` (unitários, integração, piso de cobertura e checagem de formatação) e um job separado com o `scripts/smoke-test.sh` contra o Docker Compose; o CI nasce junto com o esqueleto **Mode:** guided

## Q8. Como fica a entrega nesta release, sem ambiente remoto?

A. Sem deploy remoto; a entrega é marcada por uma tag `v1.0.0` em `main`; desfazer é voltar à tag anterior e recriar o ambiente local (`docker compose down -v` e subir de novo)
B. Outro modelo (especifique em X)
X. Other (please specify)

[Answer]: A. Sem deploy remoto; a entrega é marcada por uma tag `v1.0.0` em `main`; desfazer é voltar à tag anterior e recriar o ambiente local **Mode:** guided

## Q9. Que verificação de código roda além da formatação? (select all that apply)

Contexto: a formatação (Spotless com google-java-format) já está decidida e bloqueia o merge. A política da organização pede um linter bloqueante no CI.

A. Só os avisos do compilador (`-Xlint:all`) tratados como erro
B. SpotBugs com FindSecBugs, só nas categorias de segurança e correção de prioridade alta (serve também de análise de segurança do código)
C. Error Prone
D. Um teste ArchUnit por serviço garantindo que o domínio não depende de Spring, JPA ou AWS
E. None
X. Other (please specify)

[Answer]: B, D. (B) SpotBugs com FindSecBugs, só nas categorias de segurança e correção de prioridade alta; (D) um teste ArchUnit por serviço garantindo que o domínio não depende de Spring, JPA ou AWS **Mode:** guided

## Q10. Como as chaves e senhas do ambiente local ficam fora do repositório, mantendo o comando único?

Contexto: as regras exigem subir tudo com um comando e proíbem versionar credenciais, segredos ou chaves. O engenheiro de segurança lembra que são três chaves críticas: cifragem do PAN, HMAC do PAN e HMAC da chave de idempotência. Também há os secrets do Keycloak e as senhas do banco.

A. Um container de inicialização no Compose gera, só na primeira vez, todas as chaves e senhas em um volume local, e os serviços as leem como arquivos; exceção apenas para as credenciais fictícias do LocalStack (`test`/`test`)
B. Um script gera um `.env` ignorado pelo git antes de subir o ambiente (passam a ser dois comandos)
C. Not yet defined
X. Other (please specify)

[Answer]: X. Container de inicialização gerando tudo na 1ª execução num diretório local ignorado pelo git (./.local/secrets), montado nos serviços: chaves do PAN (cifragem e HMAC), chave HMAC da idempotência, senhas do banco e secrets dos clients do Keycloak (renderizados no realm importado). Ele também gera um environment do Postman já preenchido para importação. Exceção: test/test do LocalStack. **Mode:** guided

## Q11. Quais verificações de segurança o CI faz nesta release?

A. Trivy (segredos no código, dependências e imagens) e Dependabot; bloqueia qualquer segredo detectado e vulnerabilidade CRITICAL com correção disponível; exceções num arquivo único com justificativa e data de validade, ligadas ao ADR do Spring Boot 3.5
B. Só a varredura de segredos
C. None nesta release
X. Other (please specify)

[Answer]: X. Trivy + Dependabot. Dependabot semanal, com atualizações agrupadas e ignorando saltos de versão major do Spring Boot (diretriz 3.x, ver ADR). Exceções do Trivy só com justificativa e data de validade. (Base: opção A — bloqueia segredo detectado e CRITICAL com correção disponível.) **Mode:** guided

## Q12. Quais destas restrições você declara como regra rígida do projeto? (select all that apply)

Contexto: regra rígida vira linha `ALWAYS`/`NEVER` em `project.md` e vale para todas as etapas seguintes. Nenhuma delas duplica as regras já existentes.

A. NEVER fazer merge em `main` com o CI vermelho ou com o piso de cobertura rebaixado
B. NEVER configurar retentativa automática de testes com falha
C. NEVER usar a mesma chave para cifragem do PAN, HMAC do PAN e fingerprint de idempotência; ALWAYS impedir a inicialização com chave ausente, malformada ou repetida
D. NEVER definir valor padrão para chaves ou segredos em arquivos de configuração, Dockerfile ou Compose
E. NEVER importar Spring, JPA, AWS SDK ou Jackson em pacotes de domínio
F. NEVER expor no Actuator endpoints além de `health`, `info` e `prometheus`
G. NEVER usar imagens Docker com tag `latest` ou sem versão fixada
H. None
X. Other (please specify)

[Answer]: A, B, C, E, F, G, X. (A) NEVER fazer merge em `main` com o CI vermelho ou com o piso de cobertura rebaixado; (B) NEVER configurar retentativa automática de testes com falha; (C) NEVER usar a mesma chave para cifragem do PAN, HMAC do PAN e fingerprint de idempotência; ALWAYS impedir a inicialização com chave ausente, malformada ou repetida; (X, no lugar de D) NEVER definir valor padrão para chaves ou segredos em configuração, Dockerfile ou Compose; única exceção: as credenciais fictícias test/test do LocalStack; (E) NEVER importar Spring, JPA, AWS SDK ou Jackson em pacotes de domínio; (F) NEVER expor no Actuator endpoints além de `health`, `info` e `prometheus`; (G) NEVER usar imagens Docker com tag `latest` ou sem versão fixada **Mode:** guided

## Consolidated Summary Confirmation

Resumo das respostas:

- Branches (Q1): trunk em `main`; o commit de `develop` vai para `main` e o `develop` é aposentado.
- Entrada em `main` (Q2, Q3): merge local em `main` depois do CI verde no branch de trabalho; um commit por Bolt (squash), em Conventional Commits, com o slug no assunto: `feat(card): ... [bolt-<slug>]`.
- Walking skeleton (Q4): sim; nada além dele é construído até ele passar no smoke test e ser aprovado.
- Ordem de testes (Q5): misto; o teste crítico de cada garantia é escrito antes e visto falhando pelo motivo certo (a asserção do comportamento); os demais testes vêm depois de cada camada.
- Cobertura (Q6): 80% de linhas por módulo somando unitários e integração; exclusões fechadas em `*Application` e no pacote `config`; branches só reportadas.
- CI (Q7): `./mvnw verify` (unitários, integração, cobertura, formatação) e job separado do smoke test contra o Compose; nasce junto com o esqueleto.
- Entrega (Q8): sem deploy remoto; tag `v1.0.0` em `main`; rollback = tag anterior + recriar o ambiente local.
- Verificação de código (Q9): SpotBugs com FindSecBugs (segurança e correção, prioridade alta) e um teste ArchUnit por serviço para o domínio.
- Chaves locais (Q10): container de inicialização gera, na primeira execução, em `./.local/secrets` (ignorado pelo git), as chaves do PAN (cifragem e HMAC), a chave HMAC da idempotência, as senhas do banco e os secrets dos clients do Keycloak (renderizados no realm importado), além de um environment do Postman preenchido; exceção só para `test/test` do LocalStack.
- Segurança no CI (Q11): Trivy (segredos, dependências, imagens; bloqueia segredo e CRITICAL com correção) e Dependabot semanal, agrupado, ignorando saltos major do Spring Boot; exceções do Trivy só com justificativa e validade.
- Regras rígidas (Q12): sete novas: merge nunca com CI vermelho ou piso rebaixado; sem retentativa automática de teste; chaves distintas e validadas na inicialização; nenhum default de segredo (exceto `test/test` do LocalStack); domínio sem Spring, JPA, AWS SDK ou Jackson; Actuator só com `health`, `info` e `prometheus`; imagens sempre com versão fixada.

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
