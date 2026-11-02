# Progress

## Done
- All eight phases, including the optional MCP server.
- `scripts/check.sh` passed three times in a row from fresh clones, with its push step skipped (see "Needs Abhiram").
- `RESULTS.md` filled from real runs.

## Next
- Push, then watch the first CI run.

## Blockers
- No git remote, so nothing is pushed. See below.

## Needs Abhiram
1. **Push the repo.** The permission system blocked `git remote add origin` twice (reason given: "Remote Repoint"), and I did not work around it. Run these from `1-loanledger`:
   ```bash
   git remote add origin https://github.com/abhiramvedipala/loanledger.git
   ```
   ```bash
   git push -u origin main
   ```
   Push only `main`, not `git push --all`: see item 3.
2. **Confirm CI.** The workflow has never run, because it needs the push. The README badge is unverified until the first green run.
3. **Delete the backup branch when you're happy.** I rebuilt the unpushed history to follow the commit rules. `backup/original-history` holds the original three commits, which contain the attribution lines you don't want:
   ```bash
   git branch -D backup/original-history
   ```

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
- **`scripts/check.sh` without the skip fails at its last step** until the push is done, so the brief's goal of "check exits 0 and the project is pushed" is not met yet.
- **GitHub Actions has not run.**
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
