# LoanLedger plan

- [x] 1. Scaffold + schema: Spring Boot 3, Gradle Kotlin DSL, Compose Postgres 16, Flyway tables, deterministic seed, `/actuator/health` UP, `scripts/check.sh`
- [x] 2. Domain + API: amortization, payment allocation, loss-mit state machine, `/api/v1`, pagination, RFC 7807, Swagger UI
- [x] 3. Tests: JUnit 5 unit tests, Testcontainers integration tests, `scripts/count.sh`
- [x] 4. React dashboard: Vite + React + TS in `web/`, served by Spring under `/` in the image
- [x] 5. Docker + Kubernetes: multi-stage Dockerfile, Compose runs everything, `k8s/`, `scripts/kind-up.sh`
- [x] 6. CI: GitHub Actions build, test, Docker build; README badge
- [ ] 7. MCP server (optional, after everything else passes): Python, read-only `get_loan`, `list_delinquent_loans`, `simulate_payment`; transcript in README
- [x] 8. Load test + results: 30-second k6 run against Compose; `RESULTS.md`
- [ ] 9. Definition of done: `scripts/check.sh` passes 3x from a fresh clone, README complete, morning summary
