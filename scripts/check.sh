#!/usr/bin/env bash
# The project's definition of working. Prints PASS or the first failing check.
# SKIP_PUSH_CHECK=1 skips only the "pushed to origin" check and says so in the output.
set -uo pipefail
cd "$(dirname "$0")/.."

export COMPOSE_PROJECT_NAME=loanledger-check
export DB_PORT=25433 APP_PORT=28080
if brew --prefix openjdk@21 >/dev/null 2>&1; then export JAVA_HOME="$(brew --prefix openjdk@21)"; fi

LOG="$(mktemp)"
fail() { echo "FAIL: $1 (log: $LOG)"; tail -30 "$LOG"; exit 1; }
step() { echo "== $1"; }
cleanup() { docker compose down -v >/dev/null 2>&1; }
trap cleanup EXIT

step "clean state"
cleanup
./gradlew --no-daemon clean >"$LOG" 2>&1 || fail "gradle clean"

step "unit and integration tests"
./gradlew --no-daemon test >"$LOG" 2>&1 || fail "tests"

step "dashboard installs and type-checks"
rm -rf web/node_modules web/dist
(cd web && npm ci && npm run build) >"$LOG" 2>&1 || fail "web build"

step "README's one command brings up the stack"
docker compose up --build -d --wait >"$LOG" 2>&1 || fail "docker compose up"
BASE="http://localhost:$APP_PORT"
curl -sf "$BASE/actuator/health" | grep -q '"status":"UP"' || fail "health not UP"
curl -sf "$BASE/api/v1/loans?size=1" | grep -q '"totalElements":50' || fail "seed: expected 50 loans"
ASSET="$(curl -sf "$BASE/" | grep -o '/assets/[^"]*\.js')" || fail "dashboard not served at /"
curl -sf "$BASE$ASSET" | grep -q 'LoanLedger' || fail "dashboard bundle not served"
cleanup

step "RESULTS.md comes from the scripts"
python3 scripts/render_results.py --check >"$LOG" 2>&1 || fail "RESULTS.md does not match results/"
[ "$(scripts/count.sh)" = "$(cat results/count.txt)" ] || { scripts/count.sh > "$LOG"; fail "results/count.txt is stale; rerun scripts/results.sh"; }

step "git clean"
[ -z "$(git status --porcelain)" ] || { git status --short > "$LOG"; fail "uncommitted changes"; }

step "pushed"
if [ "${SKIP_PUSH_CHECK:-0}" = 1 ]; then
  echo "SKIPPED: push check (SKIP_PUSH_CHECK=1)"
else
  git rev-parse --verify -q origin/main >/dev/null || { echo "no origin/main" > "$LOG"; fail "not pushed: no origin/main"; }
  [ -z "$(git log origin/main..main --oneline)" ] || { git log origin/main..main --oneline > "$LOG"; fail "unpushed commits"; }
fi

echo PASS
