# Personas: CardForge Release 1.0

Base: `requirements.md` (intenção, FR1 a FR11), `stakeholder-map.md` e `user-stories-questions.md` (Q1). A release é só de APIs: as personas são sistemas clientes e equipes que dependem do comportamento do CardForge.

## P1. Gateway de onboarding

| Campo | Descrição |
|---|---|
| Papel | Único cliente técnico do CardForge: o sistema do gateway em execução e o time que o desenvolve e o integra aos contratos. Atende os canais dos varejistas parceiros (checkout, app, e-commerce) em nome do consumidor e responde pela autorização por recurso |
| Representa | Indiretamente, os varejistas parceiros (não perder nem travar adesões) e o consumidor final (não esperar pela emissão no checkout) |
| Objetivos | Cadastrar o portador sem travar o consumidor no checkout; repetir chamadas com segurança quando a rede falha; saber o desfecho da emissão para informar o parceiro |
| Dores | Picos de campanha; dependências instáveis; respostas ambíguas que não dizem se o pedido foi aceito, e erros com o mesmo código HTTP que pedem ações diferentes (repetir, corrigir, desistir) |
| Contexto técnico | Alto: consome contratos OpenAPI, token OAuth2 por client credentials, envia `Idempotency-Key` e, quando tem, `X-Actor-Id` |
| Frequência | Contínua; picos de até 50 cadastros/s |
| Prioridade | 1 (principal) |

## P2. Analista de catálogo (Processamento da RPE)

| Campo | Descrição |
|---|---|
| Papel | Mantém o catálogo de produtos (Black, Gold, Platinum...), cada um com BIN próprio; as chamadas passam pelo gateway |
| Objetivos | Criar produtos com BIN correto, ajustar nome e descrição, cancelar produtos e ter certeza de que nenhum cartão novo sai de produto cancelado |
| Dores | Erro de BIN é difícil de desfazer; incerteza sobre quando o cancelamento passa a valer |
| Contexto técnico | Médio |
| Frequência | Poucas alterações por mês |
| Prioridade | 3 |

## P3. Operador de sustentação (Processamento da RPE)

| Campo | Descrição |
|---|---|
| Papel | Opera a plataforma: acompanha alertas, solicitações retidas, DLQs e a reconciliação |
| Objetivos | Saber quando algo não chegou a desfecho e recuperar sem gerar cartão duplicado |
| Dores | Falhas silenciosas; retentativas que mascaram erro de configuração; falta de procedimento documentado |
| Contexto técnico | Alto: métricas, logs estruturados, runbooks no README |
| Frequência | Diária e em incidentes |
| Prioridade | 2 |

## P4. Segurança da Informação e Compliance/DPO

| Campo | Descrição |
|---|---|
| Papel | Interessado informado sobre os controles de dados adotados; não aprova gates (`stakeholder-map.md`) |
| Objetivos | Verificar que o PAN é protegido, que dados pessoais não vazam para logs e mensagens e que as transições são auditáveis, sem que a release afirme conformidade integral |
| Dores | Controles só declarados, sem evidência |
| Contexto técnico | Médio |
| Frequência | Revisões pontuais |
| Prioridade | 4 |

## Relações entre personas

- P1 é a porta de entrada de P2: o analista age pelas APIs, por meio do gateway.
- P3 recupera o que P1 enviou e ainda não teve desfecho.
- P4 depende das evidências (seção de controles do README e testes de não vazamento) que as histórias de P1, P3 e da entrega produzem (testes de não vazamento e documentação).
