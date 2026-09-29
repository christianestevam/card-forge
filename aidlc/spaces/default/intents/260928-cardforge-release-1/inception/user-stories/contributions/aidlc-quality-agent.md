**Collaborator:** aidlc-quality-agent

## Contribution

Foco: testabilidade de cada critério de aceite, mapeamento dos testes críticos (`TC1` a `TC10`, `TC-PAN`, `TC-IDEM`), rastreabilidade de cada regra `BR{grupo}.{seq}` e lacunas em casos de erro e de borda. Base: `stories.md`, `personas.md`, `user-stories-questions.md`, `requirements.md` e as regras de `project.md` e `team.md` (critério crítico escrito antes e visto falhando pela asserção certa).

### 1. Convenções propostas para a seção "Convenções" de `stories.md`

Estas três convenções resolvem de uma vez a maior parte dos critérios hoje não observáveis.

1. **"Alerta" observável.** Em todo critério, "gera alerta" significa: um contador Micrometer específico daquele tipo de alerta é incrementado e aparece em `/actuator/prometheus`, e é emitido um log estruturado com um código de evento estável, sem dados pessoais. Não há gerenciador de alertas no Compose (fora do escopo), então a métrica é o oráculo do teste. Os nomes das métricas e dos códigos ficam para o NFR Design. Critérios afetados: AC2.2.3, AC3.1.3, AC3.2.4 ("degradação registrada"), AC3.5.2, AC3.5.3, AC4.1.3, AC6.1.3, AC6.2.1 (BIN 70%) e os novos critérios abaixo.
2. **Tempo controlado.** Todo critério com janela de tempo (5 min do cache, 24 h da idempotência, 3 h e 30 min da reconciliação, 18 e 120 anos, validade de 5 anos) é verificado com `Clock` injetado e com os dois lados da fronteira: o último instante aceito e o primeiro recusado. "No máximo 5 minutos" é inclusivo: idade de `validatedAt` ≤ 5 min é elegível; > 5 min não é.
3. **Independência dos testes de emissão.** As histórias dos grupos 3, 4 e 6 são demonstráveis sem US2.1: a solicitação é injetada diretamente em `card-issuance-requested` (LocalStack), o resultado em `card-issuance-completed`, e o catálogo é simulado com WireMock. Isso atende a regra de histórias independentemente testáveis de `inception.md` e deve constar das Notas de INVEST no lugar de "mensagens e respostas simuladas".

### 2. Oráculo dos testes críticos

Para que cada teste crítico seja escrito antes e falhe "pela asserção do comportamento" (`team.md`), o critério precisa dizer qual é a asserção. O mapeamento TC → AC do rascunho está correto em todos os doze casos; proponho só tornar o resultado verificável:

| Teste | AC | Asserção principal (oráculo) que o critério deve declarar |
|---|---|---|
| TC1 | AC2.2.2 | Com a SQS fora: 202; portador, solicitação `PENDING` e evento de outbox persistidos; evento não marcado como enviado. Com a SQS de volta, sem ação manual: exatamente uma mensagem com o `issuanceRequestId` em `card-issuance-requested` e evento marcado como enviado. |
| TC2 | AC3.4.1 | Forçada a falha entre o commit e o `DeleteMessage`: após a reentrega, exatamente 1 cartão e 1 registro em `issuance_processing` para a solicitação; o resultado persistido é recolocado no outbox com o mesmo `cardId`; a mensagem é então confirmada. |
| TC3 | AC3.4.2 | Duas cópias da mesma mensagem processadas em paralelo: exatamente 1 cartão, 1 desfecho terminal e nenhum `cardId` diferente publicado; as duas mensagens acabam confirmadas (nenhuma vai para a DLQ). |
| TC4 | AC3.4.3 | Solicitação `FAILED` (`PRODUCT_CANCELED`) reentregue **depois de o catálogo passar a responder `ACTIVE`**: o desfecho continua `FAILED` com o mesmo motivo, o catálogo não é consultado (verificação no WireMock) e nenhum cartão é criado. Sem essa mudança no catálogo o teste passa mesmo com o código reavaliando, e não prova a garantia. |
| TC5 | AC3.3.2 | Duas solicitações **distintas** (injetadas) para o mesmo portador e produto, processadas em paralelo: exatamente 1 cartão não cancelado; a outra termina `FAILED` com `NON_CANCELED_CARD_ALREADY_EXISTS` (falha de negócio, sem retentativa), e não como falha técnica. |
| TC6 | AC3.2.3 | Registro com `validatedAt` > 5 min e catálogo indisponível: nenhum cartão, nenhuma linha em `issuance_processing`, nenhum evento de outbox; a mensagem não é confirmada e a visibilidade é ajustada pelo backoff. |
| TC7 | AC3.2.2 | Reescrever: dado que o catálogo respondeu `CANCELED` em T1 e uma resposta `ACTIVE` observada em T0 < T1 chega depois, quando essa resposta tenta gravar o cache, então o registro `ACTIVE` não é restaurado; e uma solicitação processada em seguida termina `FAILED` com `PRODUCT_CANCELED`, sem cartão. |
| TC8 | AC4.2.5 | Os quatro casos produzem combinações diferentes e fixas de (situação da emissão, indicador de completude por parte, indicador de desatualização com instante), todos com resposta de sucesso (nenhum 5xx). Os valores exatos vêm do Desenho de Contratos, mas o critério deve exigir uma tabela de quatro linhas sem colisão. |
| TC9 | AC6.1.1 | Separar em dois critérios (ver seção 4): solicitação nunca entregue ao card-service → recuperada e emitida uma única vez; solicitação já decidida → nenhum cartão novo, total de cartões inalterado. |
| TC10 | AC6.1.2 | Card-service com `ISSUED` persistido e cadastro ainda `PENDING` (resultado descartado): após a reconciliação, o cadastro mostra `ISSUED` com o mesmo `cardId` e o total de cartões continua 1. |
| TC-PAN | AC3.1.2 | Com o gerador forçado a repetir um PAN existente: exatamente 1 cartão novo com PAN diferente do colidido, 1 resultado `ISSUED` e 1 evento de outbox; nenhuma linha parcial da tentativa que colidiu. |
| TC-IDEM | AC2.3.3 | Ver seção 4: dividir em cenários determinísticos, sempre com a asserção "exatamente 1 portador, 1 solicitação e 1 evento de outbox". |

### 3. Rastreabilidade das regras `BR{grupo}.{seq}`

| Regra | Critérios que a cobrem | Situação |
|---|---|---|
| BR1.1 | AC1.1.1, AC1.1.2, AC1.1.3, AC1.3.1, AC1.3.2 | OK |
| BR1.2 | AC1.1.1, AC1.4.1, AC1.4.2, AC1.3.3 | OK |
| BR1.3 | AC1.4.3 (cartões emitidos), AC3.2.1, AC3.2.2 | OK; citar BR1.3 em AC3.2.1 e AC3.2.2, que hoje não a referenciam |
| BR2.1 | AC2.1.2 | **Parcial**: falta "armazenado só com dígitos" (novo AC2.1.7) |
| BR2.2 | AC2.1.5 | OK; falta a concorrência (novo AC2.1.8) |
| BR2.3 | AC2.1.3 | Parcial: sem fronteiras (ver seção 4) |
| BR2.4 | AC2.1.4 | Parcial: sem fronteiras (ver seção 4) |
| BR2.5 | AC2.1.1, AC5.1.1, AC5.1.2 | OK |
| BR2.6 | AC2.1.1 | **Parcial**: falta `productId` ausente ou malformado (novo AC2.1.9) |
| BR3.1 | AC2.3.1, AC2.3.5 | **Parcial**: falta o escopo por `client_id` (novo AC2.3.6) |
| BR3.2 | AC2.3.2, AC2.3.3 | OK após a divisão de AC2.3.3 |
| BR3.3 | AC2.1.6 | OK |
| BR3.4 | AC2.2.1, AC3.2.1 | OK |
| BR3.5 | AC2.1.1, AC2.2.1, AC2.2.2 | OK; falta a atomicidade da gravação (novo AC2.1.10) |
| BR4.1 | AC3.1.1, AC3.2.3, AC3.2.5 | **Parcial**: falta "ler o cache não renova `validatedAt`" (novo AC3.2.6) e Redis + catálogo fora (novo AC3.2.7) |
| BR4.2 | AC3.4.1 a AC3.4.3, AC4.1.3, AC6.1.1 | OK |
| BR4.3 | AC3.3.1, AC3.3.2, AC5.2.3 | OK, com a ressalva sobre AC5.2.3 (seção 5) |
| BR4.4 | AC3.2.1, AC3.5.1 | **Parcial**: falta o esgotamento do orçamento de retry (~2 h) com retenção e alerta (novo AC3.5.5) |
| BR4.5 | AC4.1.1 | OK |
| BR5.1 | AC3.1.1, AC3.1.2 | OK |
| BR5.2 | AC3.1.1 | OK; verificar com `Clock` (emissão em dezembro vira ano +5, mesmo mês) |
| BR5.3 | AC3.1.1, AC5.2.2 | OK |
| BR5.4 | AC3.1.1 | OK |
| BR5.5 | AC5.2.1, AC7.2.2 | OK |
| BR6.1 | AC4.2.5 | OK |
| BR6.2 | AC4.2.1 a AC4.2.5 | OK |
| BR6.3 | AC4.2.3 | **Parcial**: só o card-service indisponível; falta o catálogo indisponível sem nenhuma observação (novo AC4.2.6) |

Todas as regras têm ao menos um critério; as nove parciais precisam dos critérios da seção 4 para que "cada regra BR-* rastreável a pelo menos um teste" (`project.md`) cubra a regra inteira, e não só parte dela.

### 4. Reescritas e critérios novos

**Critérios a reescrever (termos vagos ou resultado não determinístico):**

- **AC2.3.3 (TC-IDEM)** tem "ou" no resultado, o que impede um teste determinístico. Dividir em:
  - AC2.3.3a: dada a requisição original já confirmada, quando uma segunda chega com a mesma chave e o mesmo payload, então recebe o replay (202, mesmo corpo, `Idempotent-Replayed: true`).
  - AC2.3.3b: dada a requisição original segurando o lock além do tempo limite (atraso forçado), quando a segunda chega com a mesma chave, então recebe 409.
  - AC2.3.3c: dadas duas requisições simultâneas com a mesma chave e payloads diferentes, então uma é aceita e a outra recebe 422 (ou 409, conforme a questão T2 abaixo).
  - Em todos: exatamente 1 portador, 1 solicitação e 1 evento de outbox.
- **AC3.2.5** não está em Dado/Quando/Então ("quando se mede o tempo"). Reescrever: dado um registro `ACTIVE` com `validatedAt` = T e o produto cancelado no catálogo em T + 1 min, quando uma solicitação é processada em T + 5 min + 1 s, então o catálogo é consultado e o desfecho é `FAILED` com `PRODUCT_CANCELED`; processada em T + 5 min, o registro ainda é elegível (janela aceita pela NFR7).
- **AC3.5.1**: "a próxima tentativa acontece em 30 s" contradiz o jitter. Reescrever com faixas: a visibilidade aplicada na n-ésima falha fica em [0,8 × 30 × 2^(n-1); 1,2 × 30 × 2^(n-1)] s e nunca passa de 300 s; e o catálogo recebe exatamente uma chamada por recebimento (WireMock).
- **AC3.5.4**: "em poucos minutos" é vago (proibido por `inception.md`). Reescrever: dada a dependência de volta, então a emissão retida é processada no máximo 300 s depois (teto do backoff) mais o tempo de processamento.
- **AC3.4.4**: "dado qualquer processamento" não é uma condição. Reescrever: dado o banco falhando no commit do resultado, quando o processamento termina, então a mensagem não é confirmada e volta a ficar visível.
- **AC4.1.2**: "nada muda" → nenhuma linha nova de histórico, situação e `cardId` inalterados, mensagem confirmada.
- **AC1.2.3**: dados 25 produtos, a listagem sem parâmetros retorna exatamente 20; `size=100` é aceito, `size=101` gera 400.
- **AC1.4.4 e AC5.3.4**: "procura uma operação de exclusão" → `DELETE` no recurso não é aceito (405) e não consta do OpenAPI.
- **AC5.1.1 e AC5.2.2**: enumerar as quatro transições válidas e verificar, para cada uma, o novo status persistido e a linha no histórico; enumerar as inválidas (a partir de `CANCELED` e para o mesmo status).
- **AC6.2.3**: "em todos os saltos" → o mesmo `correlationId` e o mesmo trace aparecem no log do cadastro, nos atributos da mensagem em `card-issuance-requested`, no log do card-service, nos atributos da mensagem em `card-issuance-completed` e no log do consumidor de resultados.
- **AC7.2.2**: "testes de integração críticos" é escopo ambíguo. Reescrever como: nos fluxos de cadastro, emissão e consulta, inclusive nos caminhos de erro (422 de validação, 409 de CPF duplicado, falhas de dependência), os logs e as mensagens SQS capturados não contêm o CPF, a data de nascimento nem o PAN usados. Caso de borda obrigatório: a violação de unicidade do PostgreSQL traz o valor na mensagem (`Key (cpf)=(...) already exists`); se essa exceção for registrada sem tratamento, o CPF vaza. O `toString()` de records com dados pessoais é o outro vetor.

**Critérios novos (lacunas de erro e de borda):**

- **AC2.1.7 (BR2.1):** dado um CPF enviado com pontuação válida, quando o cadastro é aceito, então é armazenado só com dígitos (e a unicidade compara os dígitos: o mesmo CPF com e sem pontuação gera 409).
- **AC2.1.8 (BR2.2):** dados dois cadastros simultâneos com o mesmo CPF e chaves diferentes, então exatamente um recebe 202 e o outro 409, pela constraint.
- **AC2.1.9 (BR2.6):** dado `productId` ausente ou malformado, então a resposta é 422 com o campo indicado e o catálogo não é consultado.
- **AC2.1.10 (BR3.5, FR2.5):** dada uma falha ao gravar qualquer um dos quatro registros (chave, portador, solicitação, outbox), então nenhum deles fica persistido, a resposta não é 202 e uma nova tentativa com a mesma chave é processada normalmente.
- **Fronteiras de AC2.1.3 e AC2.1.4:** 18 anos completos hoje é aceito, um dia antes gera 422; exatamente 120 anos é aceito, mais que isso gera 422; nome com 3 e 120 caracteres é aceito, 2 e 121 geram 422; uma única palavra gera 422.
- **AC2.2.4 (FR2.4):** dado o catálogo lento além do orçamento, então o cadastro recebe 202 dentro do orçamento e o catálogo recebeu exatamente uma chamada (sem retentativa).
- **AC2.3.6 (BR3.1):** dada a mesma chave usada por dois `client_id` diferentes, então as requisições são tratadas de forma independente (a segunda não recebe o replay da primeira).
- **AC2.3.7:** dada uma chave expirada e removida pela limpeza, quando o mesmo cadastro é reenviado com ela, então não surge um segundo portador (o CPF único devolve 409).
- **AC3.2.6 (BR4.1, FR4.2):** dado um registro `ACTIVE` com `validatedAt` = T lido várias vezes entre T e T + 5 min, quando uma solicitação é processada em T + 5 min + 1 s, então o catálogo é consultado. É a garantia de "ler o cache nunca renova `validatedAt`", hoje sem critério apesar de estar em Decided.
- **AC3.2.7 (FR4.2):** dados o Redis e o catálogo indisponíveis, sem registro elegível, então nenhum cartão é emitido e a mensagem volta para retentativa (falha técnica).
- **AC3.2.8 (FR4.2):** dado o catálogo respondendo `ACTIVE`, então o cache passa a ter um registro com `validatedAt` igual ao instante da observação.
- **AC3.5.5 (BR4.4):** dada uma falha técnica persistente até `maxReceiveCount`, então a mensagem vai para a DLQ, a solicitação continua `PENDING` no cadastro (recuperável pela reconciliação) e um alerta é gerado.
- **AC3.5.6 (NFR10):** dado o catálogo ou o card-service respondendo depois do timeout configurado, então a chamada é abortada no timeout e tratada como falha técnica; dada uma sequência de falhas que abre o circuit breaker, então as chamadas seguintes falham imediatamente, também como falha técnica, nunca como falha de negócio. Um teste útil para "nenhuma chamada remota dentro de transação de escrita": com o catálogo lento, nenhuma conexão do pool fica ativa durante a chamada.
- **AC3.6.x (FR3.1), no grupo de publicação (US2.2 ou uma história de habilitação):** dado um `SendMessageBatch` em que 1 de 10 entradas é recusada, então só as 9 aceitas são marcadas como enviadas e a recusada é reenviada no ciclo seguinte; dados dois workers (duas instâncias) sobre o mesmo outbox, então nenhum evento é enviado pelos dois (`SKIP LOCKED`).
- **AC4.2.6 (BR6.3):** dado o catálogo indisponível e nenhuma observação do produto, quando a consulta é feita, então a resposta traz portador, situação e cartão, com a parte do produto sinalizada como indisponível.
- **AC4.2.7:** dado um portador inexistente, quando a consulta consolidada é feita, então a resposta é 404.
- **AC6.1.4 (FR6.1):** com `Clock` controlado: solicitação `PENDING` com 2 h 59 min não é republicada e com 3 h 1 min é; uma solicitação republicada há 29 min não é republicada de novo; com 60 elegíveis, uma execução republica no máximo 50.
- **AC6.1.5 (BR4.2 × BR4.3):** dada uma solicitação `ISSUED` cujo cartão já foi cancelado, quando a solicitação é republicada (reconciliação ou reentrega), então nenhum cartão novo é emitido e o resultado original é republicado. O índice parcial permitiria um novo cartão; é a garantia de nunca reavaliar que impede, e um teste deve provar isso.
- **AC7.1.4:** dado um token expirado, então 401; e o escopo de cada endpoint é verificado por um teste parametrizado sobre todos os endpoints (leitura e escrita), não só pelo exemplo `cards:write`.

### 5. Pontos para triagem pelo lead

**Possível requisito sem origem:**
- **AC5.2.3** diz "quando o mesmo portador pede um novo cartão do produto", mas nenhum FR da R1 cria uma segunda solicitação de emissão para um portador existente: a única origem de solicitação é o cadastro, e o CPF é único. O comportamento do índice parcial é testável injetando a solicitação na fila, mas a redação sugere uma capacidade de API inexistente (regra de rastreabilidade de `inception.md`). Proposta: reescrever como "dada uma solicitação injetada para portador e produto com cartão cancelado, então a emissão é permitida", e registrar que não há endpoint para isso na R1.

**Questões que são escolhas legítimas (para o usuário, via arquivo de perguntas):**
- **T1.** Um cadastro recusado (400, 409 ou 422) consome a `Idempotency-Key`? FR2.6 fala em "24 h após o aceite", o que sugere que não; nesse caso, reenviar a mesma chave com o payload corrigido é processado como novo. Afeta AC2.3.1 e AC2.3.2.
- **T2.** Mesma chave, payload diferente, com a original ainda em andamento: 422 (payload diferente) ou 409 (em andamento)? Afeta AC2.3.3c.
- **T3.** Resultado recebido pelo cadastro para um `issuanceRequestId` desconhecido, ou mensagem de resultado inválida: DLQ com alerta, como em FR4.7? FR5.1 não diz.
- **T4.** Resultado contraditório (AC4.1.3): a mensagem é confirmada depois do alerta (fim do ciclo) ou vai para a DLQ? Sem isso, o teste não sabe o que verificar na fila.
- **T5.** `X-Actor-Id` com mais de 100 caracteres: 400 ou truncado? FR7.3 define o limite, mas não a reação.

**Encaminhar para etapas seguintes (não bloqueiam esta etapa):**
- AC2.2.3: o código HTTP ao receber 401/403/contrato inválido do catálogo segue em aberto (R-01); o critério deve ao menos fixar "não é 422 e não cria nada relacionado a produto inexistente".
- AC4.2.4: a origem e o limiar da "observação antiga" na consulta consolidada (qual cache o cardholder-service tem e a partir de que idade o dado é desatualizado) não estão definidos; o Desenho Funcional precisa fixar o limiar para o critério ter oráculo.
- AC2.4.1: "sem expor a data de nascimento além do necessário" (FR2.8) é vago; o Desenho de Contratos decide se ela é omitida ou mascarada.
- AC7.2.3: a chave de fingerprint vive no cardholder-service e as chaves do PAN no card-service; a verificação "igual a outra das três" exige que cada serviço leia as três de `./.local/secrets`. Confirmar no Desenho Funcional.
- Critérios de documentação (AC6.3.1, AC8.2.1, AC8.4.1) são verificados por lista de conferência na revisão, não por teste automatizado. Registrar isso nas Notas de INVEST para não contar como teste de regra BR.

## Positions

- AGREE: Tratar os comportamentos sob falha como critérios da história de negócio (Q3) mantém cada teste crítico ligado ao resultado que ele protege.
- AGREE: O mapeamento de `TC1` a `TC10`, `TC-PAN` e `TC-IDEM` aos critérios está correto em todos os doze casos; só falta declarar o oráculo de cada um.
- AGREE: Adiar NFR1 a NFR5 para NFR Requirements é coerente com a ausência de teste de carga nesta release.
- OBJECT: AC2.3.3 (TC-IDEM) tem "ou" no resultado e precisa ser dividido em cenários determinísticos, senão o teste crítico não pode falhar pelo motivo certo.
- OBJECT: AC3.2.5 e AC3.5.4 usam formas não verificáveis ("quando se mede o tempo", "em poucos minutos"), vedadas pelas regras de `inception.md`.
- OBJECT: "Gera alerta" aparece em oito critérios sem forma observável; sem a convenção de métrica e código de evento, nenhum deles tem oráculo.
- OBJECT: Falta critério para "ler o cache nunca renova `validatedAt`", uma decisão de Decided que sustenta a janela de 5 minutos da NFR7.
- OBJECT: Falta critério para o esgotamento do orçamento de retry (DLQ, solicitação `PENDING` recuperável e alerta), que é metade da regra BR4.4.
- OBJECT: AC3.4.3 (TC4) passa mesmo com código que reavalia a solicitação, a menos que o catálogo mude para `ACTIVE` antes da reentrega.
- OBJECT: AC6.1.1 (TC9) mistura dois cenários (solicitação nunca entregue e solicitação já decidida) com oráculos diferentes; devem ser dois critérios.
- OBJECT: AC5.2.3 descreve um pedido de novo cartão que nenhum FR da R1 oferece; precisa ser reescrito como injeção de solicitação ou ter a origem documentada.
