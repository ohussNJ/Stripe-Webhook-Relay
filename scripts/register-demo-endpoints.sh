#!/usr/bin/env bash
# Registers three demo subscriber endpoints against the running local server.
# Each points to a named chaos route so they appear as distinct services in logs.
# Adjust BASE_URL when running against Render.

BASE_URL="${1:-http://localhost:8080}"
ADMIN_KEY="${ADMIN_KEY:-local-admin-key}"

echo "Registering demo endpoints against $BASE_URL"

curl -s -X POST "$BASE_URL/admin/endpoints" \
  -H "Content-Type: application/json" \
  -H "X-Admin-Key: $ADMIN_KEY" \
  -d "{\"url\":\"$BASE_URL/chaos/order-service?failure_rate=0.3\",\"eventTypes\":[\"payment_intent.succeeded\",\"charge.refunded\"]}" \
  | cat

echo ""

curl -s -X POST "$BASE_URL/admin/endpoints" \
  -H "Content-Type: application/json" \
  -H "X-Admin-Key: $ADMIN_KEY" \
  -d "{\"url\":\"$BASE_URL/chaos/email-service?failure_rate=0.5\",\"eventTypes\":[\"payment_intent.succeeded\",\"payment_intent.payment_failed\"]}" \
  | cat

echo ""

curl -s -X POST "$BASE_URL/admin/endpoints" \
  -H "Content-Type: application/json" \
  -H "X-Admin-Key: $ADMIN_KEY" \
  -d "{\"url\":\"$BASE_URL/chaos/analytics?failure_rate=0.1\",\"eventTypes\":[]}" \
  | cat

echo ""
echo "Done. Run: curl -H \"X-Admin-Key: $ADMIN_KEY\" $BASE_URL/admin/endpoints to verify."
