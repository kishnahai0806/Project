#!/usr/bin/env bash
set -euo pipefail

BASE_URL=${BASE_URL:-http://localhost:8080}
DEMO_USERNAME=${DEMO_USERNAME:-demo_user}
DEMO_EMAIL=${DEMO_EMAIL:-demo_user@example.com}
DEMO_PASSWORD=${DEMO_PASSWORD:-DemoUser@123}
ADMIN_USERNAME=${ADMIN_USERNAME:-admin}
ADMIN_PASSWORD=${ADMIN_PASSWORD:-Admin@123}
IDEMPOTENCY_KEY=${IDEMPOTENCY_KEY:-event-demo-001}
EVENT_TITLE=${EVENT_TITLE:-"API Demo Event"}

HTTP_BODY=""
HTTP_CODE=""

request() {
  local response
  response=$(curl -sS -w "\n%{http_code}" "$@")
  HTTP_BODY=$(echo "$response" | sed '$d')
  HTTP_CODE=$(echo "$response" | tail -n1)
}

json_get() {
  local key="$1"
  local json="$2"
  local out

  if command -v jq >/dev/null 2>&1; then
    if out=$(echo "$json" | jq -r --arg key "$key" '.[$key] // empty' 2>/dev/null); then
      printf '%s' "$out"
      return 0
    fi
  fi

  if command -v python3 >/dev/null 2>&1; then
    if out=$(JSON_KEY="$key" JSON_PAYLOAD="$json" python3 -c "import json, os; data=json.loads(os.environ['JSON_PAYLOAD']); v=data.get(os.environ['JSON_KEY'], ''); print(v if v is not None else '')" 2>/dev/null); then
      printf '%s' "$out"
      return 0
    fi
  fi

  if command -v python >/dev/null 2>&1; then
    if out=$(JSON_KEY="$key" JSON_PAYLOAD="$json" python -c "import json, os; data=json.loads(os.environ['JSON_PAYLOAD']); v=data.get(os.environ['JSON_KEY'], ''); print(v if v is not None else '')" 2>/dev/null); then
      printf '%s' "$out"
      return 0
    fi
  fi

  if command -v powershell.exe >/dev/null 2>&1; then
    if out=$(printf '%s' "$json" | JSON_KEY="$key" powershell.exe -NoLogo -NoProfile -Command '$key = $env:JSON_KEY; $obj = [Console]::In.ReadToEnd() | ConvertFrom-Json; $prop = $obj.PSObject.Properties[$key]; if ($null -ne $prop -and $null -ne $prop.Value) { Write-Output $prop.Value }' 2>/dev/null | tr -d '\r'); then
      printf '%s' "$out"
      return 0
    fi
  fi

  echo "No JSON parser found. Install jq, python, or run from Windows with powershell.exe available." >&2
  exit 1
}

json_array_length() {
  local key="$1"
  local json="$2"
  local out

  if command -v jq >/dev/null 2>&1; then
    if out=$(echo "$json" | jq --arg key "$key" '.[$key] | length' 2>/dev/null); then
      printf '%s' "$out"
      return 0
    fi
  fi

  if command -v python3 >/dev/null 2>&1; then
    if out=$(JSON_KEY="$key" JSON_PAYLOAD="$json" python3 -c "import json, os; data=json.loads(os.environ['JSON_PAYLOAD']); v=data.get(os.environ['JSON_KEY'], []); print(len(v) if isinstance(v, list) else 0)" 2>/dev/null); then
      printf '%s' "$out"
      return 0
    fi
  fi

  if command -v python >/dev/null 2>&1; then
    if out=$(JSON_KEY="$key" JSON_PAYLOAD="$json" python -c "import json, os; data=json.loads(os.environ['JSON_PAYLOAD']); v=data.get(os.environ['JSON_KEY'], []); print(len(v) if isinstance(v, list) else 0)" 2>/dev/null); then
      printf '%s' "$out"
      return 0
    fi
  fi

  if command -v powershell.exe >/dev/null 2>&1; then
    if out=$(printf '%s' "$json" | JSON_KEY="$key" powershell.exe -NoLogo -NoProfile -Command '$key = $env:JSON_KEY; $obj = [Console]::In.ReadToEnd() | ConvertFrom-Json; $prop = $obj.PSObject.Properties[$key]; if ($null -eq $prop -or $null -eq $prop.Value) { Write-Output 0 } elseif ($prop.Value -is [System.Array] -or $prop.Value -is [System.Collections.IList]) { Write-Output $prop.Value.Count } else { Write-Output 0 }' 2>/dev/null | tr -d '\r'); then
      printf '%s' "$out"
      return 0
    fi
  fi

  echo "No JSON parser found. Install jq, python, or run from Windows with powershell.exe available." >&2
  exit 1
}

json_pretty() {
  local json="$1"

  if command -v jq >/dev/null 2>&1; then
    if echo "$json" | jq '.' 2>/dev/null; then
      return 0
    fi
  fi

  if command -v python3 >/dev/null 2>&1; then
    if printf '%s' "$json" | python3 -m json.tool 2>/dev/null; then
      return 0
    fi
  fi

  if command -v python >/dev/null 2>&1; then
    if printf '%s' "$json" | python -m json.tool 2>/dev/null; then
      return 0
    fi
  fi

  if command -v powershell.exe >/dev/null 2>&1; then
    if printf '%s' "$json" | powershell.exe -NoLogo -NoProfile -Command '$obj = [Console]::In.ReadToEnd() | ConvertFrom-Json; $obj | ConvertTo-Json -Depth 10' 2>/dev/null | tr -d '\r'; then
      return 0
    fi
  fi

  echo "$json"
}

require_non_empty() {
  local field_name="$1"
  local value="$2"
  local body="$3"

  if [ -z "$value" ]; then
    echo "Failed to parse '$field_name' from JSON response."
    echo "$body"
    exit 1
  fi
}

echo "0) Health check"
request -X GET "$BASE_URL/actuator/health"
if [ "$HTTP_CODE" != "200" ]; then
  echo "Health check failed (HTTP $HTTP_CODE). Is the app running at $BASE_URL?"
  echo "$HTTP_BODY"
  exit 1
fi

echo "1) Register user"
request -X POST "$BASE_URL/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$DEMO_USERNAME\",\"email\":\"$DEMO_EMAIL\",\"password\":\"$DEMO_PASSWORD\"}"
if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
  USER_TOKEN=$(json_get "token" "$HTTP_BODY")
elif [ "$HTTP_CODE" = "409" ]; then
  echo "User already exists; logging in with demo credentials."
  request -X POST "$BASE_URL/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$DEMO_USERNAME\",\"password\":\"$DEMO_PASSWORD\"}"
  if [ "$HTTP_CODE" != "200" ]; then
    echo "Demo user login failed after conflict (HTTP $HTTP_CODE):"
    echo "$HTTP_BODY"
    exit 1
  fi
  USER_TOKEN=$(json_get "token" "$HTTP_BODY")
else
  echo "Registration failed (HTTP $HTTP_CODE):"
  echo "$HTTP_BODY"
  exit 1
fi
require_non_empty "token" "$USER_TOKEN" "$HTTP_BODY"

echo "2) Login as admin"
request -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}"
if [ "$HTTP_CODE" != "200" ]; then
  echo "Admin login failed (HTTP $HTTP_CODE):"
  echo "$HTTP_BODY"
  exit 1
fi
ADMIN_TOKEN=$(json_get "token" "$HTTP_BODY")
require_non_empty "token" "$ADMIN_TOKEN" "$HTTP_BODY"

EVENT_PAYLOAD=$(cat <<EOF
{
  "title":"$EVENT_TITLE",
  "description":"Peer session on messaging and retries",
  "location":"Library Lab 2",
  "startTime":"2030-04-01T17:00:00Z",
  "endTime":"2030-04-01T19:00:00Z",
  "capacity":80,
  "categoryIds":[1,2]
}
EOF
)

echo "3) Create event with idempotency key (first request)"
request -X POST "$BASE_URL/api/v1/events" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d "$EVENT_PAYLOAD"
if [ "$HTTP_CODE" != "201" ] && [ "$HTTP_CODE" != "200" ]; then
  echo "Create event failed (HTTP $HTTP_CODE):"
  echo "$HTTP_BODY"
  exit 1
fi
EVENT_ID=$(json_get "id" "$HTTP_BODY")
require_non_empty "id" "$EVENT_ID" "$HTTP_BODY"
echo "Created event id: $EVENT_ID"

echo "4) Replay same create request to validate idempotency"
request -X POST "$BASE_URL/api/v1/events" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d "$EVENT_PAYLOAD"
REPLAY_ID=$(json_get "id" "$HTTP_BODY")
require_non_empty "id" "$REPLAY_ID" "$HTTP_BODY"
if [ "$HTTP_CODE" != "200" ]; then
  echo "Replay request did not return HTTP 200 as expected (actual: $HTTP_CODE)."
  echo "$HTTP_BODY"
  exit 1
fi
if [ "$REPLAY_ID" != "$EVENT_ID" ]; then
  echo "Idempotency check failed: replay id $REPLAY_ID != created id $EVENT_ID"
  exit 1
fi
echo "Idempotency replay confirmed."

echo "5) Approve event as admin"
request -X POST "$BASE_URL/api/v1/events/$EVENT_ID/approve" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"comment":"Approved from demo flow"}'
if [ "$HTTP_CODE" != "200" ]; then
  echo "Approve failed (HTTP $HTTP_CODE):"
  echo "$HTTP_BODY"
  exit 1
fi

echo "6) Browse/search approved events"
request -X GET "$BASE_URL/api/v1/events?q=API%20Demo&status=APPROVED&page=0&size=5&sort=startTime,asc"
if [ "$HTTP_CODE" != "200" ]; then
  echo "Search failed (HTTP $HTTP_CODE):"
  echo "$HTTP_BODY"
  exit 1
fi
MATCH_COUNT=$(json_array_length "content" "$HTTP_BODY")
echo "Search returned $MATCH_COUNT event(s)."

echo "7) Query weekly metrics (admin)"
request -X GET "$BASE_URL/api/v1/events/metrics/weekly?from=2026-01-01T00:00:00Z&to=2035-12-31T23:59:59Z" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
if [ "$HTTP_CODE" != "200" ]; then
  echo "Metrics query failed (HTTP $HTTP_CODE):"
  echo "$HTTP_BODY"
  exit 1
fi
json_pretty "$HTTP_BODY"

echo
echo "Demo flow completed successfully."
