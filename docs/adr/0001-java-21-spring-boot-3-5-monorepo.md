# ADR-0001: Java 21, Spring Boot 3.5.x e Maven multimódulo

- **Status:** Accepted (2026-09-27, pre-inception)
- **Contexto:** a diretriz da plataforma RPE fixa a linha Spring Boot 3.x. A 3.5 está fora do suporte OSS desde junho de 2026.
- **Decisão:** Java 21 (LTS), Spring Boot 3.5.16, Spring Cloud AWS 3.4 e um monorepo Maven com Maven Wrapper: um módulo por serviço mais o `cardforge-platform`.
- **Consequências:** stack alinhada à plataforma, mas sem correções OSS da 3.5. O Dependabot deve ignorar majors do Boot até a migração. A migração para 4.x é débito registrado.
- **Alternativas rejeitadas:** Spring Boot 4.x agora, porque foge da diretriz da plataforma e o Spring Cloud AWS 4 ainda não foi validado no time; um repositório por serviço, porque triplica o CI para uma pessoa.

**Plano de migração para 4.x:**
1. Subir para Spring Cloud AWS 4.x, springdoc 3.x e Testcontainers 2.x no mesmo passo.
2. Revisar a configuração de segurança (Spring Security 7) e o structured logging.
3. Rodar `./mvnw verify` e o smoke test.
4. Um Bolt próprio, depois da R1.
