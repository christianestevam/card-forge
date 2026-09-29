# Histórias de Usuário: plano e perguntas

Plano proposto: histórias no formato "Como [persona], quero [objetivo], para [benefício]", com critérios de aceite em Given/When/Then, prioridade MoSCoW e notas de INVEST. IDs estáveis `US{grupo}.{seq}` e `AC{grupo}.{seq}.{n}`, rastreados até os FR/NFR de `requirements.md`.

Escolha uma letra na linha de resposta de cada pergunta.

## Q1. Quais personas as histórias devem usar?

Contexto: a release é só de APIs. Quem "usa" o CardForge são sistemas e equipes.

A. Quatro: o gateway de onboarding (cliente técnico, em nome de varejistas e consumidores); o analista de catálogo do Processamento (mantém os produtos); o operador de sustentação do Processamento (retenções, reconciliação, alertas); a Segurança/Compliance (verifica os controles de dados)
B. Três: gateway, Processamento (catálogo e operação juntos) e Segurança/Compliance
C. Duas: gateway e Processamento
D. Not yet defined
X. Other (please specify)

[Answer]: A

## Q2. Como agrupar as histórias?

A. Por fluxo de negócio, alinhado ao backlog: catálogo, cadastro, emissão, aplicação do resultado e consulta, status e histórico, operação e recuperação, entrega e documentação
B. Por persona
C. Por serviço (product-service, cardholder-service, card-service)
D. Not yet defined
X. Other (please specify)

[Answer]: A

## Q3. Qual a granularidade para os comportamentos sob falha?

Contexto: os dez testes críticos e os casos de falha de cada dependência precisam aparecer como critérios verificáveis.

A. Uma história por resultado de negócio; os comportamentos sob falha viram critérios de aceite dela (ex.: "cadastro aceito com a mensageria fora" é um critério da história de cadastro), cada um ligado ao seu teste crítico
B. Uma história separada para cada comportamento sob falha
C. Not yet defined
X. Other (please specify)

[Answer]: A

## Q4. Como tratar os requisitos não funcionais nas histórias?

A. Os que têm comportamento observável (integridade, cancelamento de produto em até 5 min, retomada após falha, proteção de dados, autenticação) viram critérios de aceite; as metas de desempenho, disponibilidade e observabilidade ficam rastreadas como adiadas para NFR Requirements
B. Todos os não funcionais viram histórias próprias
C. Not yet defined
X. Other (please specify)

[Answer]: A

## Q5. Os itens de entrega (ambiente com um comando, smoke test, CI, OpenAPI, Postman, README, ADRs) entram como histórias?

A. Sim, como histórias das personas que os consomem: gateway (OpenAPI, Postman), Processamento (README, runbooks), Segurança (controles documentados); ambiente, smoke test e CI como histórias de habilitação ligadas ao esqueleto
B. Não: ficam fora das histórias e rastreados como adiados para a Delivery Planning e a Code Generation
C. Not yet defined
X. Other (please specify)

[Answer]: A

## Q6. (Mob) O que o cadastro responde quando o catálogo devolve 401, 403 ou contrato inválido?

Contexto: é a ressalva R-01 dos requisitos. Designer e desenvolvedor preferem aceitar com alerta, porque o cadastro já aceita quando não consegue confirmar o produto.

A. 202 (aceito) com alerta de configuração; a validação fica para a emissão, onde a mesma falha vira alerta e retentativa, nunca falha de negócio
B. 503 (dependência indisponível) e nada é criado
X. Other (please specify)

[Answer]:A

## Q7. (Mob) Um cadastro recusado (400, 409 ou 422) consome a `Idempotency-Key`?

A. Não: só um cadastro aceito consome a chave; reenviar a mesma chave com o payload corrigido é tratado como novo
B. Sim: a recusa também fica registrada e é devolvida em replay por 24 h
X. Other (please specify)

[Answer]:A

## Q8. (Mob) Mesma chave com payload diferente enquanto a original ainda está em andamento: qual resposta?

A. 409 (em andamento prevalece; o cliente repete depois e recebe 422 se o payload continuar diferente)
B. 422 (payload diferente prevalece)
X. Other (please specify)

[Answer]:A

## Q9. (Mob) O que o cadastro faz com um resultado de emissão para uma solicitação desconhecida ou com mensagem inválida?

A. Envia para a DLQ e gera alerta, como no card-service
B. Confirma a mensagem e gera alerta
X. Other (please specify)

[Answer]: X — A, com envio explícito e imediato para a DLQ (sem esperar o maxReceiveCount), preservando a mensagem original.

## Q10. (Mob) Resultado contraditório (ex.: `ISSUED` para uma solicitação já `FAILED`): o que acontece com a mensagem?

A. Não altera nada, gera alerta e confirma a mensagem (fim do ciclo)
B. Não altera nada, gera alerta e envia a mensagem para a DLQ
X. Other (please specify)

[Answer]: X — B, com envio explícito e imediato para a DLQ; a correção é manual, seguindo o runbook, e o card-service é a fonte da verdade sobre a decisão de emissão.

## Q11. (Mob) `X-Actor-Id` com mais de 100 caracteres: o que acontece?

A. 400, com o campo indicado
B. O valor é truncado em 100 caracteres
X. Other (please specify)

[Answer]: A

## Q12. (Mob) Pedir a mudança de status para o status atual (ex.: bloquear um cartão já bloqueado): o que acontece?

Contexto: o rascunho dizia 409; o designer aponta que uma repetição segura de uma chamada já aplicada voltaria como erro.

A. Idempotente: 200 com o estado atual, sem nova entrada no histórico
B. 409, com um `type` de erro próprio para "já está nesse status"
X. Other (please specify)

[Answer]: A

## Q13. (Mob) Na consulta consolidada, e se não houver nenhuma observação do produto utilizável (catálogo fora e nada guardado)?

Contexto: os quatro casos do brief não cobrem esse. Designer, desenvolvedor e qualidade apontaram a lacuna.

A. Quinto caso: resposta 200, com o produto sinalizado como indisponível e o restante completo
B. 503 para a consulta inteira
X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

Resumo das respostas:

- Personas (Q1): quatro: gateway de onboarding (em nome de varejistas e consumidores), analista de catálogo do Processamento, operador de sustentação do Processamento, Segurança/Compliance.
- Agrupamento (Q2): por fluxo de negócio, alinhado ao backlog: catálogo, cadastro, emissão, aplicação do resultado e consulta, status e histórico, operação e recuperação, entrega e documentação.
- Granularidade (Q3): uma história por resultado de negócio; comportamentos sob falha como critérios de aceite, cada um ligado ao seu teste crítico.
- Não funcionais (Q4): os observáveis viram critérios de aceite; desempenho, disponibilidade e observabilidade adiados para NFR Requirements.
- Itens de entrega (Q5): histórias das personas que os consomem, mais histórias de habilitação (ambiente, smoke test, CI) ligadas ao esqueleto.
- Catálogo com 401, 403 ou contrato inválido no cadastro (Q6): 202 com alerta de configuração; validação na emissão, nunca falha de negócio.
- Recusa e `Idempotency-Key` (Q7): só o aceite consome a chave; reenviar com payload corrigido é novo.
- Payload diferente com a original em andamento (Q8): 409.
- Resultado para solicitação desconhecida ou mensagem inválida no cadastro (Q9): envio explícito e imediato para a DLQ (sem esperar o maxReceiveCount), preservando a mensagem original, com alerta.
- Resultado contraditório (Q10): nada muda, alerta e envio explícito e imediato para a DLQ; correção manual pelo runbook; o card-service é a fonte da verdade da decisão de emissão.
- `X-Actor-Id` acima de 100 caracteres (Q11): 400.
- Mudança para o status atual (Q12): idempotente, 200 com o estado atual, sem nova entrada no histórico.
- Produto sem observação utilizável na consulta consolidada (Q13): quinto caso, 200 com o produto sinalizado como indisponível.

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
