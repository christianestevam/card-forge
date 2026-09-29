# Viabilidade e Restrições: perguntas

Várias respostas desta etapa já estão registradas e não serão perguntadas de novo:

- stack técnica e arquitetura (seções Tech Stack e Decided de `project.md`);
- infraestrutura AWS de produção fora desta iniciativa (seção Deployment de `project.md`);
- papéis dos interessados (Captura de Intenção).

As perguntas abaixo cobrem o que ainda falta para avaliar a viabilidade. Escolha uma letra (ou várias, onde indicado) na linha de resposta de cada pergunta.

## Q1. Qual é o prazo concreto da Release 1.0?

Contexto: o brief e as regras do projeto dizem "prazo curto, uma pessoa". A escala do escopo (três serviços, outbox, reconciliação, os dez testes críticos obrigatórios, ambiente completo em Docker Compose) torna esse número o principal fator de viabilidade.

A. Até 2 semanas
B. 3 a 4 semanas
C. 5 a 8 semanas
D. Not yet defined
X. Other (please specify)

[Answer]: X. 7 dias corridos, entrega até 29/09/2026, dedicação parcial fora do horário de trabalho **Mode:** guided

## Q2. Se o prazo apertar, qual alavanca você prefere?

Contexto: as garantias de integridade e os testes críticos são inegociáveis pelas regras do projeto. A pergunta é sobre o restante.

A. Reduzir escopo não obrigatório, na ordem de prioridade que definirmos em Definição de Escopo, sem tocar nas garantias de integridade nem nos testes críticos
B. Estender o prazo e manter o escopo inteiro
C. Entregar no prazo e registrar como débito, em ADR, os itens não críticos que ficarem incompletos
D. Not yet defined
X. Other (please specify)

[Answer]: X. Reduzir escopo não obrigatório na ordem de prioridade da Definição de Escopo, sem tocar em integridade nem nos testes críticos, e registrar cada item cortado como débito conhecido no README (e em ADR quando for decisão de arquitetura). O prazo não é estendido. **Mode:** guided

## Q3. Como tratar PCI-DSS e LGPD nesta release?

Contexto: o CardForge armazena o número do cartão (cifrado) e dados pessoais (CPF, data de nascimento). O brief diz que os controles reduzem risco, mas que a release não afirma conformidade integral, porque ela depende também de infraestrutura e processos.

A. Como controles que reduzem risco, sem afirmar conformidade integral nesta release (como diz o brief)
B. Exigir conformidade ou certificação PCI-DSS completa como critério da release
C. None
X. Other (please specify)

[Answer]: B. Exigir conformidade ou certificação PCI-DSS completa como critério da release **Mode:** guided

## Q4. Existe alguma integração externa além do gateway de onboarding e do provedor de identidade corporativo?

Contexto: no ambiente local, o provedor de identidade é simulado com Keycloak e a mensageria com LocalStack. Exemplos do que NÃO está no brief: processadora de cartões, bureau de crédito, gráfica de personalização.

A. Não: apenas o gateway de onboarding (cliente) e o provedor de identidade corporativo
B. Sim: há integração adicional (especifique em X)
C. Not identified
X. Other (please specify)

[Answer]: A. Não: apenas o gateway de onboarding (cliente) e o provedor de identidade corporativo **Mode:** guided

## Q5. Há bloqueios organizacionais conhecidos? (select all that apply)

A. None
B. Dependência do time de plataforma para colocar em produção (fora desta iniciativa)
C. Congelamento de mudanças ou prioridades concorrentes que tiram tempo da pessoa desenvolvedora (especifique em X)
D. Not identified
X. Other (please specify)

[Answer]: A. None **Mode:** guided

## Q6. Há restrição de orçamento para esta release?

A. Nenhuma além do tempo da pessoa desenvolvedora; o ambiente local não tem custo de nuvem
B. Há orçamento ou limite específico (especifique em X)
C. Not applicable
X. Other (please specify)

[Answer]: A. Nenhuma além do tempo da pessoa desenvolvedora; o ambiente local não tem custo de nuvem **Mode:** guided

## Q7. Qual é a familiaridade da pessoa desenvolvedora com as tecnologias e padrões decididos?

Contexto: entre as decisões já registradas estão Java 21 e Spring Boot, Transactional Outbox, SQS com LocalStack, Redis, Keycloak e Testcontainers. Isso afeta o risco de prazo.

A. Domínio de todos; sem curva de aprendizado relevante
B. Domínio da maioria; curva de aprendizado em alguns (especifique em X)
C. Curva de aprendizado significativa em vários
D. Not yet defined
X. Other (please specify)

[Answer]: A. Domínio de todos; sem curva de aprendizado relevante **Mode:** guided

## Q8. (Acompanhamento) A data de entrega de 29/09/2026 está correta?

Contexto: hoje é 28/09/2026. Com dedicação parcial fora do horário de trabalho (Q1), sobra cerca de um dia útil parcial. Só o núcleo que você declarou inegociável (as garantias de integridade e os dez testes críticos com Testcontainers, sobre três serviços) já é muito maior que isso. Cortar escopo (Q2) não resolve, porque o que sobra depois dos cortes ainda não cabe nesse tempo.

A. Sim, 29/09/2026 está correto; aceito o risco alto de o próprio núcleo inegociável não ficar pronto, e a avaliação deve dizer isso claramente
B. Não: os 7 dias corridos contam a partir de hoje, com entrega até 05/10/2026
C. Não: a data é outra (especifique em X)
D. Not yet defined
X. Other (please specify)

[Answer]: B. Não: os 7 dias corridos contam a partir de hoje (28/09/2026), com entrega até 05/10/2026; substitui a data informada na Q1 **Mode:** guided

## Q9. (Acompanhamento) Conformidade PCI-DSS completa como critério da release: como conciliar?

Contexto: na Q3 você escolheu exigir conformidade PCI-DSS completa. Isso conflita com três pontos já confirmados:
- o brief diz que a release não afirma conformidade integral, porque ela depende também de infraestrutura e processos;
- a infraestrutura AWS de produção está fora desta iniciativa (Q8 da Captura de Intenção);
- o escalonamento de incidentes de segurança pertence à operação em produção, fora desta release (Q11 da Captura de Intenção).

A conformidade completa inclui requisitos que esta release não controla: segmentação de rede em produção, retenção de logs de 1 ano, varreduras trimestrais, teste de intrusão, políticas e processos.

A. Controles de aplicação alinhados a PCI-DSS e LGPD (cifragem do PAN, exibição só dos 4 últimos dígitos, nada de dado sensível em logs ou mensagens, trilha de auditoria), sem afirmar conformidade integral; a conformidade completa fica com a infraestrutura e a operação em produção
B. Manter a conformidade PCI-DSS completa como critério desta release, trazendo para o escopo a infraestrutura e os processos de produção necessários
C. Controles de aplicação como em A, mais um mapeamento documentado de cada requisito PCI-DSS para quem o atende (aplicação, infraestrutura ou processo), sem afirmar conformidade integral
D. Not yet defined
X. Other (please specify)

[Answer]: X. Não há exigência de conformidade PCI-DSS completa nesta release. Adotamos controles de aplicação: PAN cifrado, exibição apenas dos 4 últimos dígitos, nada sensível em logs ou mensagens, histórico auditável de transições. Não afirmamos conformidade integral, que depende de infraestrutura e processos de produção fora do escopo. (Substitui a resposta da Q3.) **Mode:** guided

## Consolidated Summary Confirmation

Resumo das respostas:

- Prazo (Q1, Q8): 7 dias corridos a partir de 28/09/2026, com entrega até 05/10/2026, em dedicação parcial fora do horário de trabalho.
- Alavanca (Q2): reduzir escopo não obrigatório na ordem de prioridade da Definição de Escopo, sem tocar em integridade nem nos testes críticos; cada corte vira débito conhecido no README (e em ADR quando for decisão de arquitetura); o prazo não é estendido.
- Regulação (Q3, Q9): sem exigência de conformidade PCI-DSS completa; controles de aplicação (PAN cifrado, só 4 últimos dígitos, nada sensível em logs ou mensagens, histórico auditável de transições); conformidade integral depende de infraestrutura e processos de produção, fora do escopo.
- Integrações (Q4): apenas o gateway de onboarding e o provedor de identidade corporativo.
- Bloqueios (Q5): nenhum.
- Orçamento (Q6): só o tempo da pessoa desenvolvedora; ambiente local sem custo de nuvem.
- Familiaridade (Q7): domínio de todas as tecnologias e padrões decididos.

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
