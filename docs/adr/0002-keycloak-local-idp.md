# ADR-0002: Keycloak como IdP local

- **Status:** Accepted (2026-09-27)
- **Contexto:** todos os endpoints exigem OAuth2. O único cliente externo é o gateway de onboarding, que é o dono da autorização por recurso.
- **Decisão:** Keycloak com o realm `cardforge` importado de arquivo versionado. Uma audiência por serviço, entregue pelos escopos `products:*`, `cardholders:*` e `cards:*`. Clients `onboarding-gateway`, `cardholder-service` e `card-service` com client credentials. O emissor é sempre `http://localhost:8080` (`KC_HOSTNAME`), e os serviços buscam as chaves pela rede interna.
- **Consequências:** os serviços validam emissor, audiência e escopo sem descoberta OIDC em tempo de execução. As credenciais do realm são fixas e só locais (desvio D1).
- **Alternativas rejeitadas:** JWT assinado com chave local, porque não representa o IdP de produção; mTLS entre serviços, porque não autoriza por escopo e é mais caro de operar localmente.
