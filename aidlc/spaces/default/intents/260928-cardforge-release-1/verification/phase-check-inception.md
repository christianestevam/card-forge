# Verificação de fronteira: Inception → Construction

**Veredito: APROVADO.** Não há `GAP`, `ORPHAN`, alvo inválido nem ID de origem faltando nos arquivos de rastreabilidade da Inception.

## Rastreabilidade consolidada

| Arquivo | IDs de origem | OK | N/A | Deferred | GAP / ORPHAN |
|---|---|---|---|---|---|
| `inception/user-stories/traceability.json` (FR/NFR → histórias) | 69 | 64 | 0 | 5 | 0 |
| `inception/domain-design/traceability.json` (histórias → componentes) | 31 | 23 | 2 | 6 | 0 |
| `inception/units-generation/traceability.json` (histórias → unidades) | 31 | 31 | 0 | 0 | 0 |

- **Adiados (Deferred) nas histórias:** NFR1 a NFR5 (desempenho, escala e disponibilidade), para NFR Requirements.
- **Adiados no Desenho de Domínio:** US6.3, US8.2, US8.3 e US8.4 para Code Generation; US7.1 para NFR Design; US8.1 para Contract Design (já coberta em `contract-summary.md`, C1 a C3 e C6).
- **N/A no Desenho de Domínio:** US0.2 (ambiente local) e US0.3 (CI). São infraestrutura de entrega, não componentes de negócio, e estão cobertas pela U1 na Geração de Unidades.
- O Desenho de Contratos não produz rastreabilidade de requisitos. Os seus 6 contratos cobrem os 6 pontos de integração de `unit-of-work-dependency.md`.

## Checagens de fronteira

| Checagem | Resultado |
|---|---|
| Todos os requisitos rastreados até histórias | OK (69/69, 5 adiados para NFR Requirements) |
| Histórias rastreadas até componentes e unidades | OK (31/31 em unidades) |
| Unidades definidas, com grafo acíclico e esqueleto primeiro | OK (`unit-of-work-dependency.md`) |
| Plano de entrega aprovado | Pendente do gate desta etapa |

## Observações (não bloqueiam)

- **Validade das etapas concluídas:** o Desenho de Domínio aparece como alterado depois da aprovação, porque o ADR-004 foi atualizado a pedido do humano durante a Geração de Unidades. A Geração de Unidades e o Desenho de Contratos aparecem como "revalidar". A mudança foi intencional e está refletida nas duas etapas.
- **Sensor de fontes da Captura de Intenção:** a última execução apontou que o título "Sinal inicial de escopo (Initial Scope Signal)" não bate literalmente com "Initial Scope Signal". O conteúdo e as tags estão corretos; é só o nome do título.
- **Correção de requisitos no Desenho de Contratos:** FR1.1, FR1.4, AC1.1.2 e AC1.3.2 mudaram de 400 para 422, e AC2.4.3 passou a dizer que a data de nascimento é omitida (`contract-summary.md`, "Correções em artefatos anteriores"). Code Generation e Build and Test devem seguir a versão corrigida.
