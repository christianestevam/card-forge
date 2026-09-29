# Avaliação de Viabilidade: CardForge Release 1.0

Base: `intent-statement.md` (Captura de Intenção), `feasibility-questions.md` (Q1 a Q9), `product-brief.md`, `engineering-standards.md` e as seções Decided, Tech Stack, Deployment, Forbidden e Mandated de `project.md`. Não houve pesquisa de mercado neste workflow.

## Veredito

**Viável com condições.** Tecnicamente, a release não tem incógnitas relevantes:

- a stack e os padrões estão decididos e registrados;
- não há integração externa além do gateway e do provedor de identidade (Q4);
- a pessoa desenvolvedora domina todas as tecnologias (Q7);
- não há bloqueio organizacional (Q5) nem restrição de orçamento além do tempo (Q6).

O fator que decide a viabilidade é o **prazo**: 7 dias corridos, com entrega até 05/10/2026, em dedicação parcial fora do horário de trabalho, sem extensão (Q1, Q8, Q2). A declaração de intenção fixa como sucesso zero solicitações perdidas, zero cartões duplicados e comportamento sob falha comprovado por testes. Esse núcleo é inegociável e já é grande para esse tempo.

A release cabe no prazo desde que três condições sejam cumpridas:

1. **Ordem de prioridade explícita.** A Definição de Escopo precisa produzir uma ordem de prioridade com uma lista de cortes pré-aprovada. Os itens não obrigatórios saem nessa ordem, e cada corte vira débito registrado no README (e em ADR quando for decisão de arquitetura) (Q2).
2. **Esqueleto primeiro.** O primeiro incremento é o fluxo ponta a ponta verificado pelo `scripts/smoke-test.sh` (Walking Skeleton em `project.md`), antes de qualquer refinamento.
3. **Nenhum trabalho fora do brief.** Cada componente precisa se justificar por um requisito do brief (Way of Working em `project.md`).

## Viabilidade técnica

| Área | Avaliação | Base |
|---|---|---|
| Stack e arquitetura | Sem incógnitas: tudo decidido e dominado pela pessoa desenvolvedora | `project.md` (Tech Stack, Decided), Q7 |
| Integrações | Só o gateway de onboarding (cliente único) e o provedor de identidade corporativo; no ambiente local, simulados por chamadas autenticadas e Keycloak | Q4, brief §1 e §8 |
| Garantias de integridade | Padrões conhecidos e já decididos (outbox, resultado terminal persistido, idempotência por estado, constraints no banco); o custo está na quantidade de testes de concorrência e de falha, não em incerteza técnica | `project.md` (Decided, Testing Posture) |
| Ambiente de demonstração | Viável com um único comando em Docker Compose. Ele exercita a integração, mas não reproduz latência, IAM, failover nem a durabilidade da AWS | `project.md` (Deployment), brief §8 |
| Metas de desempenho | Metas de projeto. Sem teste de carga, ficam declaradas como não medidas; a disponibilidade de 99,9% não é verificável nesta release | `intent-statement.md`, brief §7 |

## Visão da plataforma (produção)

- A infraestrutura AWS de produção (SQS, ElastiCache Redis, RDS PostgreSQL, provedor de identidade corporativo) fica com o time de plataforma, em outra iniciativa. Esta release entrega serviços e contratos compatíveis com esses serviços gerenciados, mas não os provisiona nem os valida (brief §8, `project.md` Deployment).
- A resiliência de produção (multi-AZ, failover, backups, alarmes) não é demonstrável aqui. O comportamento sob falha é comprovado por testes contra dependências simuladas (LocalStack, Testcontainers), não pelo comportamento real da AWS.
- Não há estimativa de custo de nuvem, porque o ambiente local não tem esse custo (Q6).

## Visão de compliance

- **Classificação dos dados:** o PAN é dado de pagamento restrito. CPF e data de nascimento são dados pessoais (LGPD) confidenciais. O nome do portador também é dado pessoal (brief §3 e §6).
- **Postura da release:** controles de aplicação sem afirmação de conformidade integral (Q9):
  - PAN cifrado com versão da chave;
  - exibição apenas dos 4 últimos dígitos;
  - nenhum dado sensível em logs ou mensagens;
  - histórico auditável de transições.

  A conformidade completa depende de infraestrutura e processos de produção, fora do escopo.
- **Escopo PCI em produção:** o serviço que guarda o PAN cifrado fará parte do ambiente de dados de cartão. A segmentação, a gestão de chaves em KMS ou Secrets Manager e a retenção de logs ficam com a produção (engineering-standards, seção Segurança).
- **Tensão LGPD registrada:** o direito de exclusão está fora da release (brief §9), e as regras proíbem apagar portadores fisicamente (Forbidden em `project.md`). Isso é aceitável na R1, mas precisa ficar visível como débito para a evolução.

## Riscos principais

A lista completa, com probabilidade, impacto e tratamento, está em `raid-log.md`. Os três que mais pesam:

1. **Prazo insuficiente para o escopo completo:** probabilidade alta. O tratamento é a lista de cortes da Definição de Escopo e o esqueleto primeiro.
2. **Comportamento de produção não verificado:** o ambiente local não reproduz a AWS.
3. **Spring Boot 3.5 fora do suporte OSS desde junho de 2026:** o risco e o plano de migração ficam em ADR (`project.md` Tech Stack).
