# Changelog

LoanLedger has no tagged releases and no merged pull requests, so this log is grouped by build phase, in commit order. Hashes are the first and last commit of each phase. See `docs/STUDY-GUIDE.md` for how to read them.

## Phase 1: Scaffold and schema (2026-11-02, `4bff97f`..`68ec8c0`)

- Started with `.gitignore`, then added the build plan and the decision log.
- Added the Gradle 8.14 wrapper and configured Spring Boot 3.5 on Java 21.
- Added Postgres 16 to Docker Compose, with a healthcheck and host port 5433.
- Added the application entry point, with Flyway as the only owner of the schema.
- Added five Flyway tables (borrowers, loans, payments, escrow accounts, loss-mitigation cases) with CHECK constraints and a partial unique index.
- Added `scripts/check.sh` as the definition of working.

## Phase 2: Domain and API (2026-11-02, `1bc3620`..`c69124c`)

- Added the pure domain classes: `Amortization`, `LoanState` (payment allocation) and the `LoanStatus` state machine.
- Added the JPA entities and repositories for borrowers, loans, payments, escrow accounts and cases.
- Added the deterministic seed of 50 synthetic loans as a Flyway Java migration.
- Added the loan and loss-mitigation services, which post, simulate and advance cases.
- Added the REST controllers under `/api/v1`, with request and response records.
- Added RFC 7807 Problem Details for every error.

## Phase 3: Tests (2026-11-02, `e6892fe`..`10c4ac6`)

- Added amortization tests against known values for $300,000 at 6.5% over 360 months.
- Added allocation edge-case tests and state-transition tests.
- Added Testcontainers integration tests on real Postgres.
- Added `scripts/count.sh` for endpoint and test counts.
- Added `PROGRESS.md` after the first three phases.

## Phase 4: Dashboard (2026-11-02, `760cac6`..`e41b235`)

- Scaffolded Vite, React and TypeScript with exact dependency versions.
- Added a typed API client, theme tokens and layout.
- Added a hand-written SVG balance chart.
- Added the loans table and the loan detail page.
- Fixed the last year label clipping on the chart.

## Phase 5: Docker and Kubernetes (2026-11-02, `ffc6328`..`2394e69`)

- Added a multi-stage Dockerfile and the Compose app service.
- Added the Kubernetes ConfigMap, example Secret, Postgres StatefulSet, app Deployment and Service, and HorizontalPodAutoscaler.
- Added `scripts/kind-up.sh`, which deploys to kind and records the result.

## Phase 6: CI (2026-11-02, `c4b0e6e`)

- Added the GitHub Actions workflow: Gradle build and tests, the dashboard build, and the Docker image build on every push.

## Phase 7: Load test and results (2026-11-02, `5ccb44a`..`de6d104`)

- Added the 30-second k6 script for `GET /api/v1/loans`.
- Added `scripts/render_results.py`, which renders `RESULTS.md` from `results/`.
- Added `scripts/results.sh` and recorded the real numbers.
- Extended `scripts/check.sh` to the full stack and the results files.
- Marked the dashboard, container, Kubernetes, CI and load-test phases done.

## Phase 8: MCP server (2026-11-02, `c58c84b`..`b032270`)

- Added the read-only MCP server with `get_loan`, `list_delinquent_loans` and `simulate_payment`.
- Added tests against a stub HTTP server.
- Added a demo client and a transcript from a real session.
- Added the MCP tests to `scripts/check.sh` and CI, and to the count script.

## Phase 9: README (2026-11-02, `ab24a2a`..`aaa73e1`)

- Added dashboard screenshots.
- Wrote the README section by section: pitch, problem, demo, architecture, tech stack, quick start, results, how it works, testing, design decisions, known limits, project structure.
- Made the README results table generated, so `scripts/check.sh` fails if it drifts.

## Phase 10: Interview notes (2026-11-02, `3d448f5`..`5f8d51b`)

- Added `docs/INTERVIEW-NOTES.md`, one commit per project phase: scaffold and schema, domain and API, tests, dashboard, Docker and Kubernetes, CI, MCP server, load test and results.

## Phase 11: Wrap-up (2026-11-02, `c77ce03`..`b341feb`)

- Recorded the later design decisions in `DECISIONS.md`.
- Marked the MCP phase and the definition of done in the plan.
- Added the morning summary and the resume bullets to `PROGRESS.md`.

## Phase 12: After the push (2026-11-04, `8d0570b`..`000a1aa`)

- Recorded the push to GitHub and the first green CI run in `PROGRESS.md`.
- Corrected the decision log, because the backup history branch was deleted after the push.
