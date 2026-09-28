# Mapa de Interessados: CardForge Release 1.0

## Interessados e interesses

| Interessado | Interesse | Papel na decisão | Source |
|---|---|---|---|
| Pessoa desenvolvedora da release (única) | Entregar a Release 1.0 no prazo curto, construindo só o que o brief exige | Decide escopo e prioridade nos gates, dentro dos limites do brief | [Q6] [memory:M1] |
| Unidade de negócio de Processamento da RPE | Dona da plataforma; opera a plataforma e precisa de emissão íntegra, rastreável e recuperável, sem perda nem duplicidade | Influencia | [Q5] [Q2] [Q6] |
| Time do gateway de onboarding da RPE | Único cliente do CardForge e responsável pela autorização por recurso | Influencia | [Q5] [Q6] |
| Time de plataforma | Provisiona a infraestrutura AWS de produção em outra iniciativa | Influencia | [Q5] [Q6] [memory:M3] |
| Varejistas parceiros | Não perder nem travar adesões nos picos de cadastro; atendidos indiretamente pelo gateway | Influencia | [Q5] [Q2] [Q6] |
| Consumidores finais (portadores) | Titulares dos dados pessoais tratados | Influencia | [Q5] [Q6] |
| Segurança da Informação e Compliance/DPO da RPE | Controles de PCI-DSS e LGPD adotados: proteção do PAN, minimização e mascaramento de dados pessoais | Informado sobre os controles adotados | [Q5] [Q11] |

## Quem decide e quem influencia

Quem decide: a pessoa desenvolvedora da release, nos gates deste workflow, dentro dos limites do brief. [Q6]

Quem influencia: os demais interessados da tabela acima, exceto Segurança da Informação e Compliance/DPO. [Q6] [Q5] [Q11]

Quem é informado: Segurança da Informação e Compliance/DPO, sobre os controles adotados. [Q11]

O escalonamento de incidentes de segurança pertence à operação em produção, fora do escopo desta release. [Q11]

Sugestões novas de qualquer interessado só viram obrigação com aprovação em gate. [memory:M2]

## Requisitos de comunicação

| Público | Canal | Conteúdo | Cadência | Source |
|---|---|---|---|---|
| Todos os interessados | Documentação versionada no repositório, em português | README e ADRs | Versionada com o código; sem relatórios periódicos além dos gates | [Q7] |
| Time do gateway de onboarding | Documentação versionada no repositório | Contratos OpenAPI | Versionada com o código | [Q7] |
| Unidade de Processamento da RPE | Documentação versionada no repositório | Runbooks operacionais de DLQ e reconciliação | Versionada com o código | [Q7] |
| Segurança da Informação e Compliance/DPO | Documentação versionada no repositório | Controles de segurança e privacidade adotados | Versionada com o código | [Q11] [Q7] |
| Pessoa desenvolvedora da release | Gates deste workflow | Aprovação de cada etapa | Em cada gate | [Q7] [Q6] |

## Assumptions & Open Questions

None.
