#!/usr/bin/env bash
# pepal Smoke Test Suite
# Usage: ./scripts/smoke-test.sh [BASE_URL]

set -uo pipefail

BASE_URL="${1:-http://localhost:8080}"
FAILED_TESTS=0
TOTAL_TESTS=0

echo "=== Running pepal API Smoke Tests against $BASE_URL ==="

check_status() {
  local name="$1"
  local expected="$2"
  local actual="$3"
  TOTAL_TESTS=$((TOTAL_TESTS + 1))
  if [ "$actual" -eq "$expected" ]; then
    echo "  [PASS] $name (Status: $actual)"
  else
    echo "  [FAIL] $name (Expected: $expected, Got: $actual)"
    FAILED_TESTS=$((FAILED_TESTS + 1))
  fi
}

# 1. ENTRIES: Get history
echo "\n--- Entries API ---"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/entries")
check_status "GET /api/entries returns 200" 200 "$HTTP_CODE"

# 2. ENTRIES: Create daily reflection
TODAY=$(date +%Y-%m-%d)
ENTRY_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/entries" \
  -H "Content-Type: application/json" \
  -d '{"body": "Smoke test daily reflection with Hinglish: Aaj ka din bohot productive tha! 😊 --- backticks `code` included.", "entryType": "DAILY_PROMPT", "entryDate": "'"$TODAY"'", "energy": "HIGH", "mood": "GOOD"}')
HTTP_CODE=$(echo "$ENTRY_RESP" | tail -n1)
BODY=$(echo "$ENTRY_RESP" | head -n -1)
DAILY_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
check_status "POST /api/entries (daily) returns 201" 201 "$HTTP_CODE"

# 3. ENTRIES: Duplicate daily reflection for same date
DUP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/entries" \
  -H "Content-Type: application/json" \
  -d '{"body": "Second reflection for same date", "entryType": "DAILY_PROMPT", "entryDate": "'"$TODAY"'"}')
check_status "POST /api/entries duplicate daily returns 409" 409 "$DUP_CODE"

# 4. ENTRIES: Invalid inputs
BLANK_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/entries" \
  -H "Content-Type: application/json" \
  -d '{"body": "   ", "entryDate": "'"$TODAY"'"}')
check_status "POST /api/entries with blank body returns 400" 400 "$BLANK_CODE"

INVALID_ENUM=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/entries" \
  -H "Content-Type: application/json" \
  -d '{"body": "Valid body", "entryDate": "'"$TODAY"'", "mood": "INVALID_MOOD"}')
check_status "POST /api/entries with invalid mood returns 400" 400 "$INVALID_ENUM"

# 5. ENTRIES: Create freeform
FREE_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/entries" \
  -H "Content-Type: application/json" \
  -d '{"body": "Smoke test freeform entry with special characters: 100% offline!", "entryType": "FREEFORM", "entryDate": "'"$TODAY"'", "energy": "LOW", "mood": "BAD"}')
FREE_CODE=$(echo "$FREE_RESP" | tail -n1)
FREE_BODY=$(echo "$FREE_RESP" | head -n -1)
FREE_ID=$(echo "$FREE_BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
check_status "POST /api/entries (freeform) returns 201" 201 "$FREE_CODE"

# 6. ENTRIES: Update
if [ -n "$FREE_ID" ]; then
  UPDATE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE_URL/api/entries/$FREE_ID" \
    -H "Content-Type: application/json" \
    -d '{"body": "Updated freeform body", "energy": "HIGH", "mood": "GOOD"}')
  check_status "PUT /api/entries/{id} valid update returns 200" 200 "$UPDATE_CODE"

  IMMUTABLE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE_URL/api/entries/$FREE_ID" \
    -H "Content-Type: application/json" \
    -d '{"body": "Updated body", "entryDate": "2099-01-01"}')
  check_status "PUT /api/entries/{id} changing date returns 400" 400 "$IMMUTABLE_CODE"
fi

# 7. TRASH: Soft delete & restore
echo "\n--- Trash API ---"
if [ -n "$FREE_ID" ]; then
  DEL_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BASE_URL/api/entries/$FREE_ID")
  check_status "DELETE /api/entries/{id} returns 204" 204 "$DEL_CODE"

  DEL2_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BASE_URL/api/entries/$FREE_ID")
  check_status "DELETE /api/entries/{id} second delete returns 404" 404 "$DEL2_CODE"

  TRASH_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/trash")
  check_status "GET /api/trash returns 200" 200 "$TRASH_CODE"

  RESTORE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/entries/$FREE_ID/restore")
  check_status "POST /api/entries/{id}/restore returns 200" 200 "$RESTORE_CODE"

  # Delete again and permanent delete
  curl -s -X DELETE "$BASE_URL/api/entries/$FREE_ID" > /dev/null
  PERM_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BASE_URL/api/entries/$FREE_ID/permanent")
  check_status "DELETE /api/entries/{id}/permanent returns 204" 204 "$PERM_CODE"
fi

# Clean up daily entry
if [ -n "$DAILY_ID" ]; then
  curl -s -X DELETE "$BASE_URL/api/entries/$DAILY_ID" > /dev/null
  curl -s -X DELETE "$BASE_URL/api/entries/$DAILY_ID/permanent" > /dev/null
fi

# 8. PROMPTS: Today prompt and caching
echo "\n--- Daily Prompts API ---"
PROMPT1_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/prompts/today")
check_status "GET /api/prompts/today returns 200" 200 "$PROMPT1_CODE"

PROMPT2_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/prompts/today")
check_status "GET /api/prompts/today cached returns 200" 200 "$PROMPT2_CODE"

FUTURE_PROMPT=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/prompts/today?date=2099-01-01")
check_status "GET /api/prompts/today with future date returns 400" 400 "$FUTURE_PROMPT"

# 9. SETTINGS API
echo "\n--- Settings API ---"
GET_SETT=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/settings")
check_status "GET /api/settings returns 200" 200 "$GET_SETT"

PUT_SETT=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE_URL/api/settings" \
  -H "Content-Type: application/json" \
  -d '{"personaPreset": "COACH", "language": "EN"}')
check_status "PUT /api/settings (COACH) returns 200" 200 "$PUT_SETT"

CUSTOM_BLANK=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE_URL/api/settings" \
  -H "Content-Type: application/json" \
  -d '{"personaPreset": "CUSTOM", "customPersona": "   ", "language": "EN"}')
check_status "PUT /api/settings (CUSTOM blank) returns 400" 400 "$CUSTOM_BLANK"

PREVIEW_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/settings/preview?preset=GENTLE&language=EN")
check_status "GET /api/settings/preview returns 200" 200 "$PREVIEW_CODE"

# Revert settings to default GENTLE
curl -s -X PUT "$BASE_URL/api/settings" -H "Content-Type: application/json" -d '{"personaPreset": "GENTLE", "language": "EN"}' > /dev/null

echo "\n=== Smoke Test Summary ==="
echo "Total checks: $TOTAL_TESTS"
echo "Failed checks: $FAILED_TESTS"

if [ "$FAILED_TESTS" -eq 0 ]; then
  echo "All live API smoke tests PASSED!"
  exit 0
else
  echo "Some smoke tests FAILED!"
  exit 1
fi
