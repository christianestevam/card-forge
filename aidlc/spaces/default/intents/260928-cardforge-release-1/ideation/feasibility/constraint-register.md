# Registro de Restrições: CardForge Release 1.0

Restrições que limitam as decisões das próximas etapas. Base: `intent-statement.md`, `feasibility-questions.md` (Q1 a Q9), `product-brief.md` e `project.md`.

## Restrições organizacionais e de entrega

| ID | Restrição | Tipo | Negociável? | Fonte |
|---|---|---|---|---|
| C-O1 | Entrega até 05/10/2026 (7 dias corridos a partir de 28/09/2026) | Prazo | Não: o prazo não é estendido | Q1, Q8, Q2 |
| C-O2 | Uma única pessoa desenvolvedora, em dedicação parcial fora do horário de trabalho | Capacidade | Não | Q1, `project.md` Way of Working |
| C-O3 | Quando o prazo apertar, só se corta escopo não obrigatório, na ordem da Definição de Escopo; cada corte vira débito no README (e em ADR quando for arquitetura) | Governança de escopo | Não | Q2 |
| C-O4 | Garantias de integridade e testes críticos nunca entram na lista de cortes | Qualidade | Não | Q2, `project.md` Testing Posture |
| C-O5 | Cada componente se justifica por um requisito do brief; na dúvida, não construir; sugestões novas só viram obrigação com aprovação em gate | Escopo | Não | `project.md` Way of Working |
| C-O6 | Sem orçamento além do tempo da pessoa desenvolvedora; nenhum custo de nuvem nesta release | Orçamento | Não | Q6 |

## Restrições técnicas

| ID | Restrição | Negociável? | Fonte |
|---|---|---|---|
| C-T1 | Stack e arquitetura decididas (Java 21, Spring Boot 3.5.x, Maven multimódulo, PostgreSQL, Redis, SQS, Keycloak local) | Não nesta release | `project.md` Tech Stack e Decided |
| C-T2 | Tudo demonstrável localmente com um único comando, em Docker Compose | Não | `project.md` Deployment, brief §2 |
| C-T3 | O ambiente local não reproduz latência, IAM, failover nem a durabilidade da AWS; esses comportamentos não são verificáveis nesta release | Não (limitação do ambiente) | brief §8 |
| C-T4 | Testes de integração dependem apenas de Docker; nada de banco em memória | Não | `project.md` Testing Posture e Forbidden |
| C-T5 | As metas de desempenho são metas de projeto; sem teste de carga, ficam declaradas como não medidas | Não | brief §7, `intent-statement.md` |

## Restrições de integração

| ID | Restrição | Fonte |
|---|---|---|
| C-I1 | O único cliente do CardForge é o gateway de onboarding; a autorização por recurso é dele | Q4, brief §1 e §6 |
| C-I2 | Toda API exige OAuth2 com o provedor de identidade corporativo; nesta release ele é simulado por Keycloak | Q4, brief §6 e §8 |
| C-I3 | Não há outras integrações externas (processadora, bureau, personalização) | Q4, brief §9 |
| C-I4 | A infraestrutura AWS de produção é provisionada pelo time de plataforma, em outra iniciativa | `project.md` Deployment, brief §8 |

## Restrições regulatórias e de privacidade

| ID | Restrição | Fonte |
|---|---|---|
| C-R1 | Controles de aplicação alinhados a PCI-DSS e LGPD, sem afirmar conformidade integral nesta release | Q9, brief §6 |
| C-R2 | PAN armazenado cifrado, com versão da chave e identificador de unicidade derivado; as chaves ficam fora do banco e do repositório | brief §6, `project.md` Forbidden |
| C-R3 | APIs exibem apenas os 4 últimos dígitos do PAN; o CPF é mascarado nas respostas | brief §5 e §6 |
| C-R4 | CPF, data de nascimento, PAN e tokens nunca aparecem em logs nem em mensagens entre serviços | brief §6, `project.md` Forbidden |
| C-R5 | Toda transição de status é registrada com ator e instante, sem copiar dados pessoais | brief §6, `project.md` Mandated |
| C-R6 | Sem exclusão física de produtos, portadores ou cartões; o direito de exclusão (LGPD) fica fora da R1 | `project.md` Forbidden, brief §9 |
