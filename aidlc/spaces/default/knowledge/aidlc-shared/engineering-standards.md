# RPE Processing: padrões de engenharia

Convenções técnicas transversais aplicáveis ao CardForge. Decisões específicas
já aprovadas estão em `project.md` (`## Decided`); o que ainda estiver em
aberto é decidido em gate e registrado em ADR com alternativas.

## Estrutura de serviço

- Um módulo Maven por serviço, com pacotes internos: `domain`, `application`, `infrastructure`, `web`.
- `domain`: agregados, value objects e transições de negócio. Sem Spring, JPA, AWS SDK ou anotações de framework.
- `application`: casos de uso e as portas necessárias. `@Transactional` fica aqui, delimitando o caso de uso.
- `infrastructure`: persistência, SQS, Redis, clientes HTTP, criptografia.
- `web`: contratos HTTP, autenticação na borda, tradução de erros.
- Entidades JPA separadas do modelo de domínio, com mapeamento manual.
- Interfaces apenas em limites reais (repositórios, catálogo, proteção de PAN, publicação de eventos). Sem interface obrigatória por caso de uso.
- `Clock` injetado para idade, validade, janela de cache e expiração de chaves de idempotência.
- Transições de status como métodos do agregado (`block()`, `activate()`, `cancel()`) que rejeitam transições inválidas.
- Evitar: classes base genéricas (`BaseService<T>`, `BaseController<T>`), módulo de domínio compartilhado entre serviços, Strategy por enum, camadas que só repassam chamadas, eventos internos para mudanças triviais.

## Tipos de data

- Data de nascimento: `LocalDate`.
- Instantes (criação, atualização, eventos, `validatedAt`): `Instant` em UTC.
- Validade do cartão: `YearMonth`.

## APIs REST

- Recursos no plural e versionados: `/api/v1/products`, `/api/v1/cardholders`, `/api/v1/cards`.
- JSON em camelCase.
- Erros em `application/problem+json` (RFC 9457), com lista de campos em erros de validação.
- Códigos: 200/201 síncronos, 202 para aceite assíncrono com `Location`, 400 payload malformado ou header obrigatório ausente, 401/403, 404, 409 conflito de estado, unicidade ou requisição idempotente em andamento, 422 regra de negócio violada ou chave de idempotência reutilizada com payload diferente, 503 dependência indisponível sem degradação possível.
- Listagens paginadas. OpenAPI publicado por serviço.

### Idempotência do cadastro

- Header `Idempotency-Key` obrigatório no `POST /api/v1/cardholders`. Referência: Internet-Draft da IETF `draft-ietf-httpapi-idempotency-key-header` (proposta, não norma).
- Chave única por `client_id` do token.
- Tabela `idempotency_keys`: `client_id`, chave, fingerprint (HMAC do payload canônico, nunca o payload em claro), recibo de aceite, `created_at`, `expires_at`.
- A chave é adquirida com `INSERT ... ON CONFLICT DO NOTHING` na mesma transação do cadastro, com `SET LOCAL lock_timeout` curto. Se outra transação estiver criando a mesma chave além desse limite: 409.
- Chave existente: comparar o fingerprint. Igual: replay do recibo original (202, mesmo corpo, header `Idempotent-Replayed: true`). Diferente: 422.
- Retenção de 24 horas após o aceite, com limpeza periódica.
- Ordem do cadastro: validar entrada → verificar chave existente → consultar o catálogo fora da transação → transação com chave, portador, solicitação e outbox → responder após o commit.

## Chamadas síncronas

- Todo cliente HTTP tem connect timeout e read timeout explícitos.
- Nenhuma chamada remota dentro de transação de escrita.
- Consulta ao catálogo no cadastro: orçamento curto, sem retentativas.
- Consulta ao catálogo na emissão: uma tentativa por processamento; quem coordena as retentativas é a SQS.
- Circuit breaker por dependência (Resilience4j), com métricas.
- Classificar respostas: 404 e `CANCELED` são fatos de negócio; timeout, 5xx e conexão recusada são falhas transitórias; 401, 403 e erro de contrato são falhas de configuração e geram alerta, nunca "catálogo offline" nem `PRODUCT_NOT_FOUND`.

## Cache de produto (card-service)

- Adaptador explícito, sem `@Cacheable`.
- Um registro por produto: `productId`, `name`, `bin`, `status`, `validatedAt`. Chave `cardforge:product:v1:{id}`, serialização JSON.
- Emissão: usa o registro só se `validatedAt` tiver no máximo 5 minutos; senão consulta o catálogo.
- Consultas: podem usar registro mais antigo, sinalizado como desatualizado. TTL físico de 24 horas.
- Ler o cache nunca renova `validatedAt`.
- Catálogo responde `ACTIVE`: grava o registro com novo `validatedAt`. Responde `CANCELED` ou 404: remove qualquer registro `ACTIVE`, que nunca pode ser restaurado por uma resposta mais antiga.
- Redis indisponível: consulta o catálogo com timeout e registra a degradação.
- Redis e catálogo indisponíveis sem registro elegível: falha transitória (retentativa pela SQS).

## Mensageria (SQS)

- Filas Standard, cada uma com DLQ e redrive policy.
- Filas: `card-issuance-requested` e `card-issuance-completed`, com DLQs de sufixo `-dlq`.
- Envelope JSON: `eventId`, `eventType`, `eventVersion`, `occurredAt`, `correlationId`, trace context e `payload`.
- Payload mínimo para o contrato. Solicitação: `issuanceRequestId`, `cardholderId`, `productId`. Resultado: `issuanceRequestId`, `status`, `failureReason`, `cardId` quando emitido.
- A retenção da DLQ é maior que a da fila de origem: em filas Standard, a mensagem mantém o timestamp original ao ir para a DLQ.

### Outbox

- Uma tabela de outbox por serviço que publica, gravada na mesma transação do dado de negócio.
- Worker agendado dentro do próprio serviço: seleciona um lote de até 10 eventos com `FOR UPDATE SKIP LOCKED`, mantém o lock durante o envio via `SendMessageBatch` (timeout curto) e marca como enviados apenas os que tiveram sucesso individual.
- Resposta perdida ou ambígua pode gerar republicação; os consumidores absorvem duplicatas.
- A identidade do resultado (`issuanceRequestId`) é distinta da identidade de cada linha de outbox, para que a republicação de recuperação nunca seja bloqueada.
- Métricas: eventos pendentes e idade do mais antigo.

### Consumo

- ACK somente depois do commit.
- Quatro classes de falha:
  - negócio: grava o desfecho `FAILED` e confirma;
  - transitória: não confirma e aplica o backoff;
  - mensagem inválida ou versão não suportada: vai para a DLQ e gera alerta;
  - configuração: alerta, sem mascarar como indisponibilidade.
- Backoff por `ChangeMessageVisibility`: primeira retentativa 30 s após a falha, dobrando a cada falha, jitter de ±20% e teto rígido de 5 minutos aplicado depois do jitter.
- `maxReceiveCount` ≈ 29, dimensionado para um orçamento nominal de ~2 horas. É dimensionamento, não instante garantido de movimentação para a DLQ.
- Visibility timeout inicial maior que o tempo máximo esperado de processamento.

### Resultado da emissão (card-service)

- Tabela `issuance_processing`: `issuance_request_id` (único), `status`, `card_id`, `failure_reason`, `processed_at`.
- Sucesso: cartão + resultado + outbox na mesma transação. Recusa de negócio: resultado + outbox na mesma transação. Falha técnica: rollback, sem resultado.
- Solicitação já decidida: nunca reavaliar; recolocar o resultado persistido no outbox antes do ACK.

### Resultado da emissão (cardholder-service)

- Idempotência por estado: `PENDING` → aplica; mesmo resultado terminal → nada muda; resultado terminal contraditório → não altera e gera alerta.

### Reconciliação (cardholder-service)

- Job agendado que republica solicitações `PENDING` mais antigas que o orçamento de retry, com margem para o jitter.
- Lotes limitados, contador de reconciliações e instante da última tentativa por solicitação.
- Após N reconciliações sem desfecho: suspende a republicação automática daquela solicitação, marca para intervenção e alerta.
- Procedimento operacional documentado para DLQ e solicitações suspensas.

## Persistência

- PostgreSQL com Flyway; schema nunca gerado pelo Hibernate fora de testes.
- `createdAt` e `updatedAt` automáticos; `@Version` nos agregados mutáveis (protege atualizações concorrentes da mesma linha, não inserções concorrentes).
- Invariantes condicionais em índices únicos parciais:
  - `cardholders (cpf)` único;
  - `cards (cardholder_id, product_id) WHERE status <> 'CANCELED'`;
  - `cards (issuance_request_id)` único;
  - `cards (pan_hmac)` único;
  - `issuance_processing (issuance_request_id)` único;
  - `products (bin)` único.
- Conflitos esperados tratados com `ON CONFLICT` direcionado à constraint específica, nunca genérico.
- Geração de PAN: candidato com `SecureRandom`; inserção com `ON CONFLICT (pan_hmac) DO NOTHING`; até 20 tentativas; esgotar é falha técnica com alerta.
- Histórico de transições de status em tabela própria, na mesma transação da mudança.

## Segurança

- Resource Servers OAuth2 validando emissor, audiência e escopos pelo JWKS do IdP.
- PAN: AES-256-GCM com nonce único por operação e versão da chave gravada com o dado; HMAC com chave separada. Chaves vindas do ambiente (produção: Secrets Manager/KMS). Chave ausente impede a inicialização.
- Rotação de chaves documentada como débito, com atenção ao HMAC: trocar a chave muda o identificador de unicidade.
- Records e DTOs com dados pessoais nunca são logados; `toString()` sobrescrito em tipos sensíveis; sem `@Data` nesses objetos.

## Observabilidade

- Actuator: liveness sem dependências externas; readiness apenas com dependências sem fallback aceitável (na R1, o banco). Configuração obrigatória validada no startup (`@ConfigurationProperties` + `@Validated`).
- Métricas Micrometer (Prometheus): emissões por status e motivo, tempo de emissão, pendências do outbox e idade da mais antiga, profundidade das filas e DLQs, reconciliações e suspensões, hit/miss/erro do cache, estado dos circuit breakers, colisões de PAN e ocupação por BIN (alerta em 70%).
- Logs JSON com `traceId` e `correlationId`; tracing W3C propagado em HTTP e atributos SQS.

## Qualidade e entrega

- `./mvnw verify` roda testes unitários e de integração (Testcontainers: PostgreSQL, Redis, LocalStack; WireMock para HTTP).
- JaCoCo para cobertura; Spotless (google-java-format).
- GitHub Actions: build, testes e verificações a cada push e pull request.
- Imagens Docker multi-stage, usuário não root, healthcheck.
