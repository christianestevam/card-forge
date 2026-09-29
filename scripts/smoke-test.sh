#!/usr/bin/env bash
# Smoke test do esqueleto ponta a ponta, contra o ambiente de `docker compose up -d --build --wait`:
# token -> produto -> cadastro do portador -> emissão assíncrona -> /overview com o cartão emitido.
# Sai com código diferente de zero em qualquer desvio.
set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
PRODUCT_URL="${PRODUCT_URL:-http://localhost:8081}"
CARDHOLDER_URL="${CARDHOLDER_URL:-http://localhost:8082}"
CARD_URL="${CARD_URL:-http://localhost:8083}"
CLIENT_ID="${CLIENT_ID:-onboarding-gateway}"
# Segredo fixo de desenvolvimento, exclusivamente local (ver README).
CLIENT_SECRET="${CLIENT_SECRET:-onboarding-gateway-local-secret}"
ISSUANCE_TIMEOUT_SECONDS="${ISSUANCE_TIMEOUT_SECONDS:-60}"
# Limite de cada chamada individual: uma requisição pendurada falha o teste em vez de travá-lo.
CURL_LIMITS=(--connect-timeout 5 --max-time 15)
CORRELATION_ID="smoke-$(date +%s)-$RANDOM"

for tool in curl jq; do
  command -v "$tool" >/dev/null || { echo "FAIL: $tool is required" >&2; exit 1; }
done

fail() { echo "FAIL: $*" >&2; exit 1; }
step() { echo "==> $*"; }

# Faz uma chamada e confere o status; o corpo vai para stdout.
call() {
  local method="$1" url="$2" expected="$3" body="${4:-}"
  local out status
  out=$(mktemp)
  status=$(curl -sS "${CURL_LIMITS[@]}" -o "$out" -w '%{http_code}' -X "$method" "$url" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Correlation-Id: $CORRELATION_ID" \
    -H 'Content-Type: application/json' \
    ${body:+--data "$body"}) || { rm -f "$out"; fail "$method $url: request failed"; }
  if [[ "$status" != "$expected" ]]; then
    echo "Response body:" >&2; cat "$out" >&2; echo >&2; rm -f "$out"
    fail "$method $url returned $status, expected $expected"
  fi
  cat "$out"; rm -f "$out"
}

# CPF válido aleatório (dígitos verificadores calculados).
random_cpf() {
  local d=() i sum r
  for i in $(seq 0 8); do d[$i]=$((RANDOM % 10)); done
  for n in 9 10; do
    sum=0
    for ((i = 0; i < n; i++)); do sum=$((sum + d[i] * (n + 1 - i))); done
    r=$(((sum * 10) % 11)); [[ $r -eq 10 ]] && r=0
    d[$n]=$r
  done
  printf '%s' "${d[@]}"
}

step "Token (client credentials: $CLIENT_ID)"
TOKEN=$(curl -sS -f "${CURL_LIMITS[@]}" -X POST "$KEYCLOAK_URL/realms/cardforge/protocol/openid-connect/token" \
  -d grant_type=client_credentials -d client_id="$CLIENT_ID" -d client_secret="$CLIENT_SECRET" \
  | jq -er .access_token) || fail "could not obtain token"

step "Create product"
BIN=$(printf '%08d' $(((RANDOM * 32768 + RANDOM) % 100000000)))
PRODUCT=$(call POST "$PRODUCT_URL/api/v1/products" 201 \
  "{\"name\":\"Smoke Gold\",\"description\":\"Smoke test product\",\"bin\":\"$BIN\"}")
PRODUCT_ID=$(jq -er .id <<<"$PRODUCT")
[[ $(jq -r .status <<<"$PRODUCT") == ACTIVE ]] || fail "product is not ACTIVE"
echo "    productId=$PRODUCT_ID bin=$BIN"

step "Register cardholder"
RECEIPT=$(call POST "$CARDHOLDER_URL/api/v1/cardholders" 202 \
  "{\"cpf\":\"$(random_cpf)\",\"fullName\":\"Smoke Test Holder\",\"birthDate\":\"1990-01-15\",\"productId\":\"$PRODUCT_ID\"}")
CARDHOLDER_ID=$(jq -er .cardholderId <<<"$RECEIPT")
REQUEST_ID=$(jq -er .issuanceRequestId <<<"$RECEIPT")
echo "    cardholderId=$CARDHOLDER_ID issuanceRequestId=$REQUEST_ID"

step "Wait for issuance (up to ${ISSUANCE_TIMEOUT_SECONDS}s)"
deadline=$((SECONDS + ISSUANCE_TIMEOUT_SECONDS))
while :; do
  OVERVIEW=$(call GET "$CARDHOLDER_URL/api/v1/cardholders/$CARDHOLDER_ID/overview" 200)
  ISSUANCE_STATUS=$(jq -r .issuance.status <<<"$OVERVIEW")
  case "$ISSUANCE_STATUS" in
    ISSUED) break ;;
    PENDING) ;;
    *) echo "$OVERVIEW" | jq . >&2; fail "issuance ended as $ISSUANCE_STATUS" ;;
  esac
  ((SECONDS < deadline)) || { echo "$OVERVIEW" | jq . >&2; fail "issuance still PENDING after ${ISSUANCE_TIMEOUT_SECONDS}s"; }
  sleep 1
done

step "Check overview and card"
[[ $(jq -r .issuance.issuanceRequestId <<<"$OVERVIEW") == "$REQUEST_ID" ]] || fail "overview has another issuance request"
[[ $(jq -r .card.availability <<<"$OVERVIEW") == AVAILABLE ]] || fail "card details not available in overview"
[[ $(jq -r .product.availability <<<"$OVERVIEW") == CURRENT ]] || fail "product not CURRENT in overview"
CARD_ID=$(jq -er .issuance.cardId <<<"$OVERVIEW")
LAST_FOUR=$(jq -er .card.data.panLastFour <<<"$OVERVIEW")
[[ "$LAST_FOUR" =~ ^[0-9]{4}$ ]] || fail "panLastFour '$LAST_FOUR' is not 4 digits"

CARD=$(call GET "$CARD_URL/api/v1/cards/$CARD_ID" 200)
[[ $(jq -r .panLastFour <<<"$CARD") == "$LAST_FOUR" ]] || fail "panLastFour differs between overview and card-service"
[[ $(jq -r .status <<<"$CARD") == ACTIVE ]] || fail "card is not ACTIVE"
[[ $(jq -r .productId <<<"$CARD") == "$PRODUCT_ID" ]] || fail "card has another product"

echo "OK: card $CARD_ID issued (**** $LAST_FOUR) for cardholder $CARDHOLDER_ID; correlationId=$CORRELATION_ID"
