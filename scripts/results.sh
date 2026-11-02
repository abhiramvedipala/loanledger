#!/usr/bin/env bash
# Measures everything RESULTS.md reports, then renders it. Run scripts/kind-up.sh first for the kind row.
set -euo pipefail
cd "$(dirname "$0")/.."
if brew --prefix openjdk@21 >/dev/null 2>&1; then export JAVA_HOME="$(brew --prefix openjdk@21)"; fi

export COMPOSE_PROJECT_NAME=loanledger-results DB_PORT=25434 APP_PORT=28082
trap 'docker compose down -v >/dev/null 2>&1' EXIT
mkdir -p results

./gradlew --no-daemon clean test
scripts/count.sh | tee results/count.txt

docker compose up --build -d --wait
k6 run --quiet --summary-export results/k6-summary.json -e BASE_URL="http://localhost:$APP_PORT" scripts/loadtest.js

python3 scripts/render_results.py
cat RESULTS.md
