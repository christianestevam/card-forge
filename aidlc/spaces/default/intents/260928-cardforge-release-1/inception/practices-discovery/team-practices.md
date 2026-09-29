# Práticas do time — CardForge

> Práticas afirmadas na entrevista da Practices Discovery (Q1–Q12). Elas
> especializam `aidlc/spaces/default/memory/org.md` e não repetem o que
> `aidlc/spaces/default/memory/project.md` já afirma; onde o detalhe está lá,
> esta página apenas o referencia.

## Way of Working

- Trabalhamos com desenvolvimento baseado em trunk: `main` é o único tronco. O commit que hoje está só em `develop` é levado para `main`, e o branch `develop` é aposentado; não mantemos branches de longa duração.
- Todo trabalho acontece em branches curtos (resolvidos em 1 a 2 dias). Os worktrees da Construction usam `main` como base e como destino do merge.
- O trabalho entra em `main` por merge local, feito somente depois de o CI ficar verde no branch de trabalho. Com uma pessoa só, não há segundo revisor humano: os gates do AI-DLC e o CI fazem esse papel.
- Cada Bolt entra em `main` como um único commit, por squash. No branch de trabalho seguimos com commits pequenos, como pede `project.md`; em `main` fica um commit por Bolt. As duas coisas são compatíveis.
- As mensagens de commit seguem Conventional Commits, em inglês. O commit de squash que entra em `main` cita o Bolt no assunto, no formato `feat(card): issue card with product validation [bolt-<slug>]`.
- A entrega é marcada por tag em `main` (`v1.0.0`), nunca por branch de release.

## Walking Skeleton

- Sim: construímos primeiro a fatia fina ponta a ponta definida em `project.md` (`## Walking Skeleton`), e ela passa por todas as etapas por unidade, inclusive Code Generation, antes das demais unidades.
- Nada além do esqueleto é construído até ele passar no comando de verificação da Construction (`scripts/smoke-test.sh` contra o ambiente de `docker compose up -d --build`) e o humano aprovar o checkpoint do esqueleto. Uma revisão de design não demonstra um esqueleto funcionando.
- O CI nasce junto com o esqueleto, para que todo incremento seguinte já passe pelos mesmos gates.

## Testing Posture

- **Methodology**: custom
- **Ordering**: O teste crítico de cada garantia é escrito antes da sua implementação e visto falhando pelo motivo certo (a asserção do comportamento, não um erro de compilação ou de setup); os demais testes unitários e de integração são escritos depois da implementação de cada camada.
- Testes são entregáveis de primeira classe em todo Bolt: nenhuma garantia fecha sem os seus testes críticos verdes. Os tipos de teste, os testes críticos obrigatórios e a rastreabilidade BR-* estão em `project.md` (`## Testing Posture`).
- Piso de cobertura: 80% de linhas por módulo de serviço (JaCoCo), medido somando os testes unitários e de integração (os dois resultados são combinados antes da verificação do piso). As exclusões são fechadas: a classe `*Application` e o pacote `config`. A cobertura de branches é apenas reportada, sem piso próprio.
- O piso e a lista de exclusões nunca são rebaixados ou ampliados para fazer uma etapa passar.
- CI: `./mvnw verify` roda os unitários (`*Test`), os de integração (`*IT`), a verificação do piso de cobertura, o `spotless:check` e o SpotBugs com FindSecBugs; um job separado executa `scripts/smoke-test.sh` contra o ambiente em Docker Compose. Os dois são criados junto com o esqueleto.

## Deployment

- Nesta release não há deploy remoto: o destino é o ambiente completo em Docker Compose descrito em `project.md` (`## Deployment`), subido com um único comando.
- A entrega é marcada pela tag `v1.0.0` em `main`. Desfazer uma entrega é voltar à tag anterior e recriar o ambiente local (`docker compose down -v` e subir de novo), já que dados e chaves locais são descartáveis e têm o mesmo ciclo de vida.
- Um deploy só está concluído quando o smoke test passa, não quando o comando de subida termina.
- Mantemos a postura da organização para quando houver ambientes: deploy automático em staging no merge e produção atrás de aprovação manual separada. Nesta release não existe staging nem produção.
- Segredos locais: na primeira execução, um container de inicialização do Compose gera em `./.local/secrets` (ignorado pelo git) as chaves de cifragem e de HMAC do PAN, a chave HMAC da idempotência, as senhas do banco e os secrets dos clients do Keycloak, que são renderizados no realm importado a partir de um template versionado. O diretório é montado nos serviços, que leem os valores como arquivos. O mesmo container gera um environment do Postman já preenchido para importação. A única exceção são as credenciais fictícias `test`/`test` do LocalStack.
- Segurança no CI: Trivy varre segredos, dependências e imagens, e bloqueia qualquer segredo detectado e qualquer vulnerabilidade CRITICAL com correção disponível. O Dependabot roda semanalmente, com atualizações agrupadas, ignorando saltos de versão major do Spring Boot (a diretriz é a linha 3.x, ver o ADR do Spring Boot 3.5). Exceções do Trivy só entram com justificativa e data de validade, ligadas a esse ADR.

## Code Style

- Seguimos a configuração de formatação e verificação versionada no repositório; a sugestão de estilo de um agente só vale para o que essa configuração não cobre.
- Formatação: Spotless com google-java-format (`project.md`); o `spotless:check` bloqueia o build e o merge.
- Lint e análise estática: SpotBugs com FindSecBugs, restrito às categorias de segurança e de correção de prioridade alta; um achado bloqueia o build e o merge.
- Limites de camada: um teste ArchUnit por serviço garante que o domínio não depende de Spring, JPA, AWS SDK ou Jackson; ele roda no `./mvnw verify` e bloqueia o merge.
- Nomenclatura idiomática e em inglês, seguindo a linguagem ubíqua do product brief; pacote base, DTOs, `ProblemDetail` e enums conforme `project.md` (`## Code Style`) e `engineering-standards.md`.
