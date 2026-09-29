# Regras rígidas descobertas — CardForge

> Restrições rígidas declaradas pelo humano na entrevista (Q12) que ainda não
> constam de `aidlc/spaces/default/memory/project.md`. A promoção as anexa ao
> `project.md`.

## Mandated

- ALWAYS impedir a inicialização quando uma chave de cifragem do PAN, HMAC do PAN ou fingerprint de idempotência estiver ausente, malformada ou igual a outra dessas chaves

## Forbidden

- NEVER fazer merge em main com o CI vermelho ou com o piso de cobertura rebaixado
- NEVER configurar retentativa automática de testes com falha
- NEVER usar a mesma chave para cifragem do PAN, HMAC do PAN e fingerprint de idempotência
- NEVER definir valor padrão para chaves ou segredos em configuração, Dockerfile ou Compose; única exceção: as credenciais fictícias test/test do LocalStack
- NEVER importar Spring, JPA, AWS SDK ou Jackson em pacotes de domínio
- NEVER expor no Actuator endpoints além de health, info e prometheus
- NEVER usar imagens Docker com tag latest ou sem versão fixada
