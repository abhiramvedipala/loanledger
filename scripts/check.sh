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
cleanup() {
  [ -n "${APP_PID:-}" ] && kill "$APP_PID" 2>/dev/null
  docker compose down -v >/dev/null 2>&1
}
trap cleanup EXIT

step "clean state"
cleanup
./gradlew --no-daemon clean >"$LOG" 2>&1 || fail "gradle clean"

step "unit and integration tests"
./gradlew --no-daemon test >"$LOG" 2>&1 || fail "tests"

step "service runs and answers"
./gradlew --no-daemon bootJar >"$LOG" 2>&1 || fail "bootJar"
docker compose up -d --wait db >"$LOG" 2>&1 || fail "compose up db"
SERVER_PORT=$APP_PORT DB_URL="jdbc:postgresql://localhost:$DB_PORT/loanledger" \
  "${JAVA_HOME:+$JAVA_HOME/bin/}java" -jar build/libs/loanledger-0.1.0.jar >"$LOG" 2>&1 &
APP_PID=$!
BASE="http://localhost:$APP_PORT"
for _ in $(seq 1 60); do curl -sf "$BASE/actuator/health" >/dev/null && break; sleep 2; done
curl -sf "$BASE/actuator/health" | grep -q '"status":"UP"' || fail "health not UP"
curl -sf "$BASE/api/v1/loans?size=1" | grep -q '"totalElements":50' || fail "seed: expected 50 loans"
cleanup

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
