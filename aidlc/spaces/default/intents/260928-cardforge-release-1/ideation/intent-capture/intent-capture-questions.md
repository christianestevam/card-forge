# Captura de Intenção: perguntas de enquadramento

As opções abaixo foram montadas a partir do `product-brief.md`. Nada do brief entra nos artefatos sem a sua confirmação aqui. Escolha uma letra por pergunta (ou várias, onde indicado) e escreva a resposta na linha de resposta logo abaixo de cada pergunta.

## Sources

- [desc] Initial description: "Implementar a Release 1.0 do CardForge, plataforma de emissão de cartões da RPE, conforme product-brief.md e engineering-standards.md em aidlc/spaces/default/knowledge/aidlc-shared/ e as decisões registradas em project.md. Três microsserviços (product-service, cardholder-service, card-service) integrados por REST e SQS, com cache Redis. A release vai para produção com prazo curto e uma pessoa desenvolvendo: construa apenas o que o brief exige e priorize integridade dos dados, emissão idempotente e comportamento definido sob falha de cada dependência."
- [scope] Workflow-selected scope: `mvp`.
- [memory:M1] `aidlc/spaces/default/memory/project.md#Way of Working`: "Release com prazo curto, desenvolvida por uma pessoa. Cada componente se justifica por um requisito do product brief; na dúvida, não construir."
- [memory:M2] `aidlc/spaces/default/memory/project.md#Way of Working`: "Sugestões novas são classificadas (requisito, decisão aprovada, opção em avaliação, fora da release) e não viram obrigação sem aprovação em gate."
- [memory:M3] `aidlc/spaces/default/memory/project.md#Deployment`: "Infraestrutura AWS de produção fora desta iniciativa."

## Q1. Qual é o problema de negócio que a Release 1.0 resolve?

Contexto: o brief descreve a adesão do consumidor no checkout, no app e no e-commerce do varejista, com picos sazonais, e diz que o CardForge separa o cadastro do portador da emissão do cartão.

A. Dar à RPE uma plataforma própria que separa o cadastro do portador da emissão do cartão, com a promessa de que nenhuma solicitação aceita se perde: toda solicitação chega a um desfecho (cartão emitido ou falha explicada) ou fica rastreável e recuperável, com alerta
B. Apenas acelerar a emissão de cartões que hoje é lenta
C. Substituir um sistema legado de emissão
D. Not yet defined
X. Other (please specify)

[Answer]: A. Separar cadastro e emissão: plataforma própria que separa o cadastro do portador da emissão do cartão, com a promessa de que nenhuma solicitação aceita se perde; toda solicitação chega a um desfecho (cartão emitido ou falha explicada) ou fica rastreável e recuperável, com alerta. **Mode:** guided

## Q2. Quem é o cliente e que dor ele sente?

Contexto: pelo brief, os canais dos varejistas não chamam o CardForge direto; tudo passa pelo gateway de onboarding da RPE.

A. Cliente externo: varejistas parceiros (cartões private label e cobranded) e seus consumidores, atendidos por meio do gateway de onboarding da RPE; a dor é perder ou travar adesões nos picos de campanha
B. Cliente interno: a unidade de Processamento da RPE, que precisa de uma emissão confiável e auditável
C. Ambos: A e B
D. Not identified
X. Other (please specify)

[Answer]: X. Cliente principal: varejistas parceiros (private label e cobranded) e seus consumidores, atendidos via gateway de onboarding da RPE; a dor é perder ou travar adesões nos picos de cadastro, com o consumidor esperando no checkout. Stakeholder interno: a unidade de Processamento da RPE, que opera a plataforma e precisa de emissão íntegra, rastreável e recuperável, sem perda nem duplicidade. **Mode:** guided

## Q3. Como vamos medir o sucesso da release? (select all that apply)

Contexto: o brief traz metas não funcionais; números de desempenho só contam como medidos se houver teste de carga.

A. Nenhuma solicitação aceita se perde por falha entre banco e mensageria; toda solicitação tem exatamente um desfecho
B. Metas de desempenho do brief como metas de projeto: cadastro p95 < 300 ms; emissão p95 < 5 s e p99 < 60 s com dependências saudáveis; consultas de cartão e produto p95 < 200 ms
C. Disponibilidade de 99,9% por serviço (meta de projeto, dependente da infraestrutura de produção)
D. Tudo demonstrável localmente com um único comando, com o fluxo ponta a ponta verificado automaticamente
E. Not yet defined
X. Other (please specify)

[Answer]: B, C, D, X. (B) Metas de desempenho do brief como metas de projeto; (C) disponibilidade de 99,9% por serviço; (D) tudo demonstrável localmente com um único comando e o fluxo ponta a ponta verificado automaticamente; (X) Nenhuma solicitação aceita se perde por falha entre banco e mensageria; cada solicitação tem no máximo um desfecho terminal, que nunca é reavaliado; solicitações sem desfecho permanecem rastreáveis e recuperáveis, com alerta. (X) Zero cartões duplicados por solicitação e zero emissões para produto inexistente ou cancelado além da janela de 5 minutos; cadastro segue aceito com catálogo, mensageria ou Redis indisponíveis; comportamento sob falha de cada dependência comprovado pelos testes críticos automatizados. **Mode:** guided

## Q4. O que dispara esta iniciativa agora?

A. Colocar em produção a primeira release da plataforma de emissão, com prazo curto
B. Os picos sazonais de adesão (Black Friday, Natal, campanhas) exigem um cadastro que não dependa da emissão estar disponível
C. Ambos: A e B
D. Not identified
X. Other (please specify)

[Answer]: C. Ambos: colocar em produção a primeira release da plataforma de emissão, com prazo curto (A), e os picos sazonais de adesão (Black Friday, Natal, campanhas), que exigem um cadastro que não dependa da emissão estar disponível (B). **Mode:** guided

## Q5. Quem são os principais interessados? (select all that apply)

A. Unidade de negócio de Processamento da RPE, dona da plataforma
B. Time do gateway de onboarding da RPE, único cliente do CardForge e responsável pela autorização por recurso
C. Time de plataforma, que provisiona a infraestrutura AWS de produção em outra iniciativa
D. Varejistas parceiros, atendidos indiretamente pelo gateway
E. Not identified
X. Other (please specify)

[Answer]: A, B, C, D, X. (A) Unidade de negócio de Processamento da RPE, dona da plataforma; (B) time do gateway de onboarding da RPE, único cliente do CardForge e responsável pela autorização por recurso; (C) time de plataforma, que provisiona a infraestrutura AWS de produção em outra iniciativa; (D) varejistas parceiros, atendidos indiretamente pelo gateway; (X) Consumidores finais (portadores), titulares dos dados pessoais tratados; Segurança da Informação e Compliance/DPO da RPE, interessados nos controles de PCI-DSS e LGPD adotados (proteção do PAN, minimização e mascaramento de dados pessoais). **Mode:** guided

## Q6. Quem decide escopo e prioridade, e quem influencia?

Contexto: as regras do projeto dizem que cada componente precisa se justificar por um requisito do brief e que sugestões novas só viram obrigação com aprovação em gate.

A. Você (a pessoa que desenvolve) decide nos gates, dentro dos limites do brief; os demais interessados influenciam
B. A unidade de Processamento decide; você influencia e executa
C. Um tech lead e um product owner decidem em conjunto; você executa
D. Not yet defined
X. Other (please specify)

[Answer]: A. Você (a pessoa que desenvolve) decide nos gates, dentro dos limites do brief; os demais interessados influenciam. **Mode:** guided

## Q7. Há exigência de comunicação ou de cadência de relatórios?

A. Nenhuma além das aprovações nos gates deste workflow
B. A comunicação é a própria documentação versionada (README e ADRs em português)
C. Relatório periódico de andamento para os interessados (especifique a cadência em X)
D. Not applicable
X. Other (please specify)

[Answer]: X. A comunicação é a documentação versionada no repositório, em português: README, ADRs, contratos OpenAPI (para o time do gateway) e runbooks operacionais de DLQ e reconciliação (para o Processamento); sem relatórios periódicos além dos gates. **Mode:** guided

## Q8. O workflow foi iniciado no escopo `mvp` (sem as etapas de operação em produção). Isso corresponde à fronteira de produto que você quer?

A. Sim, confirmo: a fronteira é a Release 1.0 como descrita no brief, incluindo o ambiente local em Docker Compose, com os itens da seção 9 do brief (e a infraestrutura AWS de produção) fora
B. Não: quero definir outra fronteira de produto (descreva em X)
C. Not yet defined
X. Other (please specify)

[Answer]: A. Sim, confirmo: a fronteira é a Release 1.0 como descrita no brief, incluindo o ambiente local em Docker Compose, com os itens da seção 9 do brief (e a infraestrutura AWS de produção) fora. **Mode:** guided

## Q9. Como a declaração do problema deve descrever a garantia de desfecho de cada solicitação?

Contexto: pedido de revisão no gate; a Q1 reproduzia o brief ("toda solicitação chega a um desfecho").

A. Manter a formulação da Q1
B. Cada solicitação tem no máximo um desfecho terminal, nunca reavaliado; sem desfecho, permanece rastreável e recuperável, com alerta; não afirmar que toda solicitação chega a um desfecho
C. Not yet defined
X. Other (please specify)

[Answer]: X. Alinhe a declaração do problema à regra registrada: cada solicitação tem no máximo um desfecho terminal, nunca reavaliado; sem desfecho, permanece rastreável e recuperável, com alerta. Não afirme que toda solicitação chega a um desfecho. Use linguagem de negócio na declaração do problema; mantenha termos técnicos apenas nas métricas. **Mode:** gate feedback

## Q10. Como tratar a meta de disponibilidade de 99,9% por serviço?

Contexto: pedido de revisão no gate, a partir da ressalva do revisor sobre a dependência da infraestrutura de produção.

A. Manter como critério de sucesso desta release
B. Remover das métricas
C. Not yet defined
X. Other (please specify)

[Answer]: X. Mantenha a disponibilidade de 99,9% como meta de produção, explicitando que depende da infraestrutura de produção e não é verificável nesta release. **Mode:** gate feedback

## Q11. Qual o papel de Segurança da Informação e Compliance/DPO, e onde fica o escalonamento de incidentes de segurança?

Contexto: pedido de revisão no gate, a partir da ressalva do revisor sobre o modelo de decisão.

A. Apenas influenciadores, sem escalonamento definido
B. Coaprovadores dos gates desta release
C. Not yet defined
X. Other (please specify)

[Answer]: X. Registre Segurança e Compliance/DPO como interessados informados sobre os controles adotados; o escalonamento de incidentes de segurança pertence à operação em produção, fora do escopo desta release. **Mode:** gate feedback

## Consolidated Summary Confirmation

Resumo das respostas:

- Problema (Q1, Q9): plataforma própria que separa o cadastro do portador da emissão do cartão; nenhuma solicitação aceita se perde; cada solicitação tem no máximo um desfecho terminal, nunca reavaliado, e sem desfecho permanece rastreável e recuperável, com alerta; declaração do problema em linguagem de negócio, termos técnicos só nas métricas.
- Cliente (Q2): cliente principal são os varejistas parceiros (private label e cobranded) e seus consumidores, via gateway de onboarding; a dor é perder ou travar adesões nos picos, com o consumidor esperando no checkout. A unidade de Processamento é interessada interna, que opera a plataforma e precisa de emissão íntegra, rastreável e recuperável, sem perda nem duplicidade.
- Sucesso (Q3): metas de desempenho do brief como metas de projeto; 99,9% de disponibilidade por serviço como meta de produção, dependente da infraestrutura de produção e não verificável nesta release (Q10); demonstração local com um único comando e fluxo ponta a ponta verificado automaticamente; nenhuma solicitação perdida entre banco e mensageria, no máximo um desfecho terminal nunca reavaliado; zero cartões duplicados e zero emissões para produto inexistente ou cancelado além da janela de 5 minutos; cadastro aceito com catálogo, mensageria ou Redis indisponíveis; comportamento sob falha comprovado pelos testes críticos automatizados.
- Gatilho (Q4): primeira release em produção com prazo curto e os picos sazonais de adesão.
- Interessados (Q5): Processamento da RPE; time do gateway de onboarding; time de plataforma; varejistas parceiros; consumidores finais (portadores); Segurança da Informação e Compliance/DPO da RPE.
- Decisão (Q6, Q11): você decide nos gates, dentro do brief; os demais influenciam; Segurança e Compliance/DPO são informados sobre os controles adotados; o escalonamento de incidentes de segurança pertence à operação em produção, fora desta release.
- Comunicação (Q7): documentação versionada em português (README, ADRs, contratos OpenAPI para o gateway, runbooks de DLQ e reconciliação para o Processamento); sem relatórios periódicos além dos gates.
- Escopo (Q8): confirmada a fronteira da Release 1.0 do brief, com ambiente local em Docker Compose; seção 9 do brief e infraestrutura AWS de produção fora.

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
