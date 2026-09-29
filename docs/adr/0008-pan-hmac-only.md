# ADR-0008: PAN guardado só como HMAC e últimos 4 dígitos

- **Status:** Accepted (R1, desvio D3 em relação a FR4.5)
- **Contexto:** o PAN precisa ser único (BR5.1) e só `panLastFour` é exposto (BR5.5). Nenhum consumidor da R1 precisa recuperar o PAN.
- **Decisão:** o PAN existe só em memória durante a emissão. O banco guarda `pan_hmac` (HMAC-SHA256 com chave dedicada em `./.local/secrets`, validada no startup) e `pan_last_four`. A unicidade usa `ON CONFLICT (pan_hmac)` com até 20 tentativas.
- **Consequências:** nenhum PAN recuperável em repouso, uma superfície menor que a cifragem prevista. Trocar a chave HMAC muda o identificador de unicidade, então a rotação exige plano (débito).
- **Alternativas rejeitadas:** AES-256-GCM com versão da chave (FR4.5), adiado por prazo e sem consumidor; hash sem chave (SHA-256), vulnerável a força bruta no espaço de 10⁷ números por BIN.
