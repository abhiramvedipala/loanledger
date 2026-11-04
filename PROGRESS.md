# Progress

## Done
- All eight phases, including the optional MCP server.
- Pushed to github.com/abhiramvedipala/loanledger; first CI run green.
- `scripts/check.sh` passed three times in a row from fresh clones, with its push step skipped, before the repo had a remote; it now passes in full.
- `RESULTS.md` filled from real runs.

## Next
- Nothing. The repo is pushed and the first CI run (#41924310748) passed all three jobs.

## Blockers
- None.

## Needs Abhiram
- Nothing. On 2026-11-04 you approved the push: `main` is on GitHub, `scripts/check.sh` passes with no skip, and the `backup/original-history` branch is deleted.

# Morning summary

## What works
- The REST API: loans, amortization schedule, payments with interest, principal and escrow allocation, simulate, payment history, loss-mitigation cases and delinquent loans, with pagination, RFC 7807 errors and Swagger UI.
- The React dashboard, served by Spring at `/`. I clicked through it in a browser: table, detail, chart, an over-limit payment being rejected, a real payment, opening a case and moving it to FORBEARANCE.
- Docker Compose, with one command for the whole stack.
- Kubernetes on kind: from a fresh cluster, 2/2 replicas ready with 0 restarts, health UP, 50 loans through the Service. The HPA reads CPU once metrics-server has scraped. The cluster has been deleted to free memory.
- The read-only MCP server, with tests and a transcript from a real session.
- More than 80 small commits starting with `.gitignore`, all authored by you, with no attribution lines on `main`.

## How to run it
```bash
docker compose up --build
```
Then open <http://localhost:8080/>. Use this to re-verify everything except the push:
```bash
SKIP_PUSH_CHECK=1 scripts/check.sh
```

## What is not verified or is limited
- `scripts/check.sh` passes in full, including the push step, and the first GitHub Actions run passed.
- The fresh-clone runs used a fresh clone but shared this machine's Gradle, npm and Docker caches, so they don't prove a first-ever download works.
- No authentication, and a retried POST can post a payment twice. README "Known limits" has the full list.
- The p95 is from one laptop, with the load generator, app and database sharing the machine and other containers on the VM at the time. Describe it as a local baseline.
- There is no Docker Desktop on this Mac, unlike what the brief expected. I created a new Colima VM (4 CPU, 8 GB), which is still running. Stop it with `colima stop`.

## Resume bullets
Every number is from `RESULTS.md`. The latency bullet says "local" on purpose; keep that wording.

- Built a cloud-native mortgage-servicing microservice covering loans, payments, escrow and loss mitigation, as measured by 11 documented REST endpoints, by designing a Java 21 and Spring Boot 3 API on PostgreSQL with Flyway migrations and RFC 7807 errors.
- Safeguarded payment allocation and loan status rules, as measured by 38 automated tests with 0 failures, by isolating the money rules in pure domain classes and running integration tests with Testcontainers on real PostgreSQL.
- Achieved a 61.77 ms p95 latency at 788 requests/s with 0.00% failed requests across 23,672 requests in a local 30-second k6 load test, by serving paginated reads with a capped page size and a single-join query that avoids N+1 lookups.
- Deployed the service to Kubernetes with 2/2 replicas ready and 0 pod restarts during rollout, as measured by a scripted kind deployment, by writing probes, an autoscaler and a Postgres StatefulSet, and packaging the app and React dashboard in one multi-stage Docker image.
