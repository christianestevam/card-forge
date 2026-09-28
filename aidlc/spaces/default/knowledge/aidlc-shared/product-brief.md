# CardForge: Product Brief

Plataforma de emissão de cartões da RPE (Retail Payment Ecosystem S/A),
unidade de negócio de Processamento. Referência de produto da Release 1.0,
destinada à produção. Padrões técnicos transversais estão em
`engineering-standards.md`; decisões já aprovadas estão na seção `## Decided`
de `aidlc/spaces/default/memory/project.md`.

## 1. Contexto de negócio

A RPE opera meios de pagamento para o varejo: cartões próprios (private label)
e cobranded oferecidos por redes varejistas parceiras. A adesão do consumidor
acontece no checkout da loja, no app e no e-commerce do varejista, o que gera
picos sazonais de cadastro (Black Friday, Natal, campanhas de adesão).

Os canais dos varejistas não chamam o CardForge diretamente: todas as
chamadas passam pelo gateway de onboarding interno da RPE, que autentica o
parceiro e autoriza o acesso aos recursos.

O CardForge desacopla o cadastro do portador da emissão do cartão. A promessa
de negócio: nenhuma solicitação aceita se perde. Toda solicitação chega a um
desfecho (cartão emitido ou falha explicada) ou permanece rastreável e
recuperável, com alerta e procedimento operacional definido.

## 2. Restrições de entrega

- Release com prazo curto, desenvolvida por uma pessoa.
- Cada componente precisa se justificar por um requisito deste brief. Na dúvida, não construir (YAGNI).
- Tudo precisa ser demonstrável localmente com um único comando.
- Toda sugestão nova é classificada como: requisito obrigatório (este brief), decisão aprovada (`## Decided`), opção em avaliação (decidida em gate, com ADR) ou evolução fora da release (seção 9). Sugestões não viram obrigações automaticamente.

## 3. Linguagem ubíqua

O negócio fala português; o código usa inglês. Esta tabela é a fonte da verdade
para nomes de classes, tabelas, campos, endpoints, eventos e filas.

| Termo de negócio | Termo no código | Definição |
|---|---|---|
| Produto | `Product` | Tipo de cartão do catálogo (ex.: Black, Gold, Platinum), com BIN próprio |
| Portador | `Cardholder` | Pessoa física titular do cartão |
| Cartão | `Card` | Instrumento de pagamento emitido para um portador a partir de um produto |
| Emissão | `Issuance` | Processo de gerar um cartão a partir de uma solicitação |
| Solicitação de emissão | `IssuanceRequest` | Pedido de emissão criado no cadastro do portador |
| Situação da emissão | `IssuanceStatus` | `PENDING`, `ISSUED`, `FAILED` |
| Motivo da falha | `IssuanceFailureReason` | `PRODUCT_NOT_FOUND`, `PRODUCT_CANCELED`, `NON_CANCELED_CARD_ALREADY_EXISTS` |
| Recibo de aceite | `RegistrationReceipt` | Resposta estável do cadastro: `cardholderId` e `issuanceRequestId` |
| Ativo / Bloqueado / Cancelado | `ACTIVE` / `BLOCKED` / `CANCELED` | Status de ciclo de vida |
| Número do cartão | `pan` | Primary Account Number; dado de pagamento protegido |
| BIN | `bin` | Prefixo de 8 dígitos do PAN que identifica o produto nesta plataforma |
| Final do cartão | `panLastFour` | Últimos 4 dígitos; única parte do PAN exibida |
| Validade | `expirationDate` | Mês e ano de expiração |
| CPF | `cpf` | Documento do portador; dado pessoal (LGPD) |

## 4. Serviços e responsabilidades

### product-service (Catálogo)

- Dono do catálogo de produtos.
- Operações: criar, consultar por ID, listar com paginação, atualizar dados descritivos e cancelar.
- Sem exclusão física: produto é cancelado, nunca apagado.
- Atributos: `id`, `name`, `description`, `bin`, `status`, `createdAt`, `updatedAt`.

### cardholder-service (Cadastro)

- Dono dos dados do portador e da solicitação de emissão como vista pelo cliente.
- Operações: cadastrar portador com o produto desejado, consultar portador, consultar a visão consolidada e alterar status do portador.
- No cadastro, cria a solicitação de emissão e a publica de forma assíncrona para o card-service.
- Mantém a situação da emissão a partir dos resultados publicados pelo card-service.

### card-service (Core de processamento)

- Dono da entidade cartão e da decisão de emissão.
- Consome solicitações de emissão, valida o produto, gera o cartão e publica o resultado.
- Operações: consultar cartão por ID, listar cartões de um portador e alterar status do cartão.
- Mantém cache Redis do catálogo de produtos.

## 5. Regras de negócio

### Produto

- BR-P1: `bin` com exatamente 8 dígitos numéricos, único no catálogo e imutável após a criação.
- BR-P2: Transições: `ACTIVE` → `CANCELED`. `CANCELED` é terminal.
- BR-P3: Cancelar um produto impede novas emissões. Cartões já emitidos continuam válidos.

### Portador

- BR-H1: CPF obrigatório, com dígitos verificadores válidos; sequências repetidas são rejeitadas. Armazenado normalizado (somente dígitos).
- BR-H2: CPF é único em toda a base na R1. Portador cancelado não pode ser recadastrado nesta release.
- BR-H3: Idade mínima de 18 anos na data do cadastro. Data de nascimento no futuro ou há mais de 120 anos é inválida.
- BR-H4: Nome completo com 3 a 120 caracteres, contendo nome e sobrenome.
- BR-H5: Transições: `ACTIVE` ↔ `BLOCKED`; `ACTIVE` ou `BLOCKED` → `CANCELED` (terminal). O portador nasce `ACTIVE`.
- BR-H6: O cadastro informa o produto desejado (`productId`).

### Cadastro

- BR-R1: O cadastro exige o header `Idempotency-Key`. Repetir a requisição com a mesma chave, pelo mesmo cliente, devolve o mesmo recibo de aceite durante 24 horas após o aceite.
- BR-R2: Mesma chave com payload diferente é rejeitada. Requisição repetida enquanto a original ainda está em processamento recebe conflito.
- BR-R3: Produto comprovadamente inexistente ou cancelado no momento do cadastro é rejeitado imediatamente, e nada é criado.
- BR-R4: Se não for possível confirmar o produto por falha técnica, o cadastro é aceito e a validação acontece na emissão.
- BR-R5: A indisponibilidade do catálogo ou da mensageria não impede nem perde um cadastro.

### Emissão

- BR-I1: A emissão só é autorizada por uma observação do produto como `ACTIVE` feita há no máximo 5 minutos. O card-service é a autoridade dessa decisão. Existe uma janela residual entre a observação e a gravação do cartão; ela é aceita e limitada a esses 5 minutos.
- BR-I2: Cada solicitação tem exatamente um desfecho terminal. Uma solicitação já decidida nunca é reavaliada: `FAILED` nunca vira `ISSUED`, e vice-versa.
- BR-I3: Um portador tem no máximo um cartão não cancelado por produto (bloquear não encerra o vínculo).
- BR-I4: Falhas de negócio (produto inexistente, cancelado, cartão não cancelado existente) encerram a emissão como `FAILED`, com o motivo e sem novas tentativas. Falhas técnicas são retentadas automaticamente por um orçamento nominal de aproximadamente 2 horas; depois disso, a solicitação fica retida para recuperação operacional, com alerta.
- BR-I5: O resultado da emissão fica visível na consulta do portador.

### Cartão

- BR-C1: PAN de 16 dígitos: `bin` do produto + 7 dígitos de conta gerados aleatoriamente + dígito verificador de Luhn. O PAN é único.
- BR-C2: Validade de 5 anos a partir da emissão (mês e ano).
- BR-C3: O cartão nasce `ACTIVE`. Transições: `ACTIVE` ↔ `BLOCKED`; `ACTIVE` ou `BLOCKED` → `CANCELED` (terminal).
- BR-C4: CVV não é gerado nem armazenado; ele pertence à personalização física, fora do escopo.
- BR-C5: APIs exibem apenas `panLastFour`.

### Consulta consolidada

- BR-V1: Retorna portador, situação da emissão, cartão (quando emitido) e produto.
- BR-V2: A resposta separa o estado de negócio da completude da informação:
  - `PENDING` sem cartão: ausência esperada, resposta completa.
  - `FAILED` sem cartão: desfecho de negócio, resposta completa.
  - `ISSUED` com card-service indisponível: parte indisponível, sinalizada.
  - Produto vindo de observação antiga: disponível, sinalizado como desatualizado, com o instante da observação.
- BR-V3: Indisponibilidade de uma dependência não derruba a consulta inteira.

## 6. Segurança, privacidade e auditoria

- Todas as APIs exigem OAuth2 com JWT emitido pelo IdP corporativo, validando emissor, audiência e escopos (ex.: `products:read`, `products:write`, `cardholders:read`, `cardholders:write`, `cards:read`, `cards:write`). Chamadas entre serviços usam client credentials.
- A autorização por recurso (qual parceiro pode ver qual portador) é responsabilidade do gateway de onboarding, único cliente autorizado do CardForge. O CardForge registra o cliente técnico e, quando informado pelo gateway, o ator original.
- PAN armazenado de forma ilegível (criptografia autenticada, com versão da chave junto ao dado) e com um identificador de unicidade derivado por HMAC com chave própria. As chaves ficam fora do banco e do repositório.
- CPF e data de nascimento são dados pessoais: nunca em logs nem em mensagens; CPF mascarado nas respostas.
- Mensagens entre serviços carregam o payload mínimo para o contrato, sem CPF, data de nascimento ou PAN.
- Toda transição de status é registrada em histórico: entidade, transição, ator e instante, sem copiar dados pessoais.
- Os controles acima reduzem risco; esta release não afirma conformidade integral com PCI-DSS ou LGPD, que depende também de infraestrutura e processos.

## 7. Requisitos não funcionais

Metas de projeto. Números de desempenho são verificados por medição quando
houver teste de carga; caso contrário, são declarados como metas não medidas.

| Tema | Meta |
|---|---|
| Volume | ~5 mil cadastros por dia em média (~1,8 milhão por ano); base de ~2 milhões de portadores ao fim do primeiro ano |
| Pico | Campanhas de até 50 cadastros/s; arquitetura preparada para escalar horizontalmente até 100/s |
| Latência do cadastro | p95 < 300 ms (o cadastro não espera a emissão) |
| Tempo de emissão | p95 < 5 s e p99 < 60 s do cadastro ao cartão, com dependências saudáveis |
| Consultas | p95 < 200 ms para cartão e produto |
| Catálogo | Menos de 100 produtos; poucas alterações por mês |
| Cancelamento de produto | Bloqueia novas emissões em até 5 minutos |
| Retomada após falha | Com a dependência de volta, as emissões retidas em retentativa retomam em poucos minutos |
| Disponibilidade | 99,9% por serviço |
| Integridade | Nenhuma solicitação aceita se perde por falha entre banco e mensageria; durabilidade frente a perda de banco ou região depende da infraestrutura de produção |
| Observabilidade | Health checks, métricas de negócio e técnicas, logs estruturados e rastreamento distribuído entre HTTP e filas |

## 8. Ambiente

- Produção: AWS (SQS, ElastiCache Redis, RDS PostgreSQL, IdP corporativo), provisionada pelo time de plataforma em outra iniciativa.
- Esta release entrega os serviços, os contratos e um ambiente local em Docker Compose (LocalStack para SQS, Keycloak como IdP). O ambiente local exercita a integração, mas não reproduz latência, IAM, failover e durabilidade da AWS.

## 9. Fora do escopo da Release 1.0

- Personalização física, CVV e HSM; ativação, limites, autorização de transações e faturas.
- Cascata de status entre portador e cartões.
- Recadastro de portador cancelado; atualização cadastral; direito de exclusão (LGPD).
- Renovação com sobreposição e substituição de cartão.
- Isolamento por parceiro dentro do CardForge (feito pelo gateway).
- Procedimento automatizado de rotação de chaves (documentado como débito).
- Expansão de faixas de BIN (monitorada por métrica de ocupação).
- Interface administrativa para DLQ e reconciliação.
- Provisionamento da infraestrutura AWS.
