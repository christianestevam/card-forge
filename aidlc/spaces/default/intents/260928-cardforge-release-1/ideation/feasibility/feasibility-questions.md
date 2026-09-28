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

[Answer]:

## Q2. Se o prazo apertar, qual alavanca você prefere?

Contexto: as garantias de integridade e os testes críticos são inegociáveis pelas regras do projeto. A pergunta é sobre o restante.

A. Reduzir escopo não obrigatório, na ordem de prioridade que definirmos em Definição de Escopo, sem tocar nas garantias de integridade nem nos testes críticos
B. Estender o prazo e manter o escopo inteiro
C. Entregar no prazo e registrar como débito, em ADR, os itens não críticos que ficarem incompletos
D. Not yet defined
X. Other (please specify)

[Answer]:

## Q3. Como tratar PCI-DSS e LGPD nesta release?

Contexto: o CardForge armazena o número do cartão (cifrado) e dados pessoais (CPF, data de nascimento). O brief diz que os controles reduzem risco, mas que a release não afirma conformidade integral, porque ela depende também de infraestrutura e processos.

A. Como controles que reduzem risco, sem afirmar conformidade integral nesta release (como diz o brief)
B. Exigir conformidade ou certificação PCI-DSS completa como critério da release
C. None
X. Other (please specify)

[Answer]:

## Q4. Existe alguma integração externa além do gateway de onboarding e do provedor de identidade corporativo?

Contexto: no ambiente local, o provedor de identidade é simulado com Keycloak e a mensageria com LocalStack. Exemplos do que NÃO está no brief: processadora de cartões, bureau de crédito, gráfica de personalização.

A. Não: apenas o gateway de onboarding (cliente) e o provedor de identidade corporativo
B. Sim: há integração adicional (especifique em X)
C. Not identified
X. Other (please specify)

[Answer]:

## Q5. Há bloqueios organizacionais conhecidos? (select all that apply)

A. None
B. Dependência do time de plataforma para colocar em produção (fora desta iniciativa)
C. Congelamento de mudanças ou prioridades concorrentes que tiram tempo da pessoa desenvolvedora (especifique em X)
D. Not identified
X. Other (please specify)

[Answer]:

## Q6. Há restrição de orçamento para esta release?

A. Nenhuma além do tempo da pessoa desenvolvedora; o ambiente local não tem custo de nuvem
B. Há orçamento ou limite específico (especifique em X)
C. Not applicable
X. Other (please specify)

[Answer]:

## Q7. Qual é a familiaridade da pessoa desenvolvedora com as tecnologias e padrões decididos?

Contexto: entre as decisões já registradas estão Java 21 e Spring Boot, Transactional Outbox, SQS com LocalStack, Redis, Keycloak e Testcontainers. Isso afeta o risco de prazo.

A. Domínio de todos; sem curva de aprendizado relevante
B. Domínio da maioria; curva de aprendizado em alguns (especifique em X)
C. Curva de aprendizado significativa em vários
D. Not yet defined
X. Other (please specify)

[Answer]:
