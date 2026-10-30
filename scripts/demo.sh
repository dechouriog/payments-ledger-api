#!/usr/bin/env bash
# Recorre el flujo completo contra la API corriendo en localhost:8080.
# Requiere curl y openssl (en Windows vienen con Git Bash).
set -euo pipefail

API=${API:-http://localhost:8080}
WEBHOOK_SECRET=${WEBHOOK_SECRET:-secreto-de-webhooks-local}

campo() { sed -E "s/.*\"$1\":\"?([^\",}]+).*/\1/"; }
paso() { printf '\n\033[1m%s\033[0m\n' "$1"; }

paso "1. Token de prueba"
TOKEN=$(curl -s -X POST "$API/auth/token" | campo accessToken)
AUTH="Authorization: Bearer $TOKEN"
echo "ok"

paso "2. Cuentas: cliente y comercio"
CLIENTE=$(curl -s -X POST "$API/accounts" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":"Cliente","currency":"COP"}' | campo id)
COMERCIO=$(curl -s -X POST "$API/accounts" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":"Tienda Don José","currency":"COP"}' | campo id)
echo "cliente=$CLIENTE"
echo "comercio=$COMERCIO"

KEY="pedido-$(date +%s)"
BODY="{\"payerAccountId\":\"$CLIENTE\",\"payeeAccountId\":\"$COMERCIO\",\"amountMinor\":2500000,\"currency\":\"COP\"}"

paso "3. Pago de \$25.000 COP con Idempotency-Key=$KEY"
RESP=$(curl -s -X POST "$API/payments" -H "$AUTH" -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" -d "$BODY")
echo "$RESP"
PAGO=$(echo "$RESP" | campo id)

paso "4. Reintento con la misma clave (no crea otro pago)"
curl -s -i -X POST "$API/payments" -H "$AUTH" -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" -d "$BODY" \
  | grep -iE "^HTTP|^Idempotent-Replayed"

paso "5. Webhook firmado del proveedor: COMPLETED"
EVENTO="{\"paymentId\":\"$PAGO\",\"status\":\"COMPLETED\"}"
FIRMA=$(printf '%s' "$EVENTO" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" -hex | awk '{print $NF}')
curl -s -X POST "$API/webhooks/payments" -H "Content-Type: application/json" -H "X-Signature: $FIRMA" -d "$EVENTO"
echo

paso "6. Saldos calculados desde el ledger"
curl -s "$API/accounts/$COMERCIO/balance" -H "$AUTH"; echo
curl -s "$API/accounts/$CLIENTE/balance" -H "$AUTH"; echo
