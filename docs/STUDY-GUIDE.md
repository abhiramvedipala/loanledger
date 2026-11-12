# Study guide: LoanLedger

This file is my map of the repository for interview prep: what to open, in what order, and which numbers I can quote.

## In one minute

LoanLedger is a mortgage-servicing microservice: it posts payments, tracks escrow and moves loans through loss mitigation, with a React dashboard on top. All data is synthetic.

Stack: Java 21, Spring Boot 3.5, Spring Data JPA, PostgreSQL 16, Flyway, React with TypeScript and Vite, Docker Compose, Kubernetes on kind, GitHub Actions, k6, and a read-only Python MCP server.

The three results I quote most, copied from [RESULTS.md](../RESULTS.md):

| Metric | Value | Command that produced it |
|---|---|---|
| `GET /api/v1/loans` p95 latency | 61.77 ms | `scripts/results.sh` |
| Throughput | 788 requests/s | `scripts/results.sh` |
| Automated tests | 38 | `scripts/count.sh` (after `./gradlew test`) |

The latency and throughput are a local baseline: k6, the app and Postgres shared one laptop. Say "local" when I quote them.

## How the system works, in five steps

Following one `POST /api/v1/loans/{id}/payments`:

1. **Validate at the edge.** `LoanController.postPayment` in `src/main/java/com/loanledger/api/LoanController.java` takes a `@Valid` `PaymentRequest`. Bad input becomes an RFC 7807 problem through `ProblemHandler` (`handleMethodArgumentNotValid`, which names each bad field).
2. **Load and check.** `LoanService.post` in `service/LoanService.java` loads the loan and its escrow account, then `LoanService.compute` rejects a date before origination or a paid-off loan with a 422 (`Problems.unprocessable`).
3. **Apply the money rules.** `LoanState.apply` in `domain/LoanState.java` is a pure function: interest first, then principal, then escrow, with any extra to principal. A partial payment stops where it runs out and leaves the rest due; a full installment advances the due date through the private `LoanState.installment`. `Amortization.monthlyInterest` and `Amortization.money` in `domain/Amortization.java` do the decimal work.
4. **Save in one transaction.** Still inside `LoanService.post` (`@Transactional`): `loan.setState`, `escrow.deposit`, and `payments.save(new Payment(...))`. The `Payment` constructor in `domain/Payment.java` derives `amount` from the allocation. The `@Version` column on `Loan` turns a concurrent write into a 409 via `ProblemHandler.concurrentUpdate`.
5. **The database has the last word.** `src/main/resources/db/migration/V1__create_schema.sql` holds the CHECK constraints (a payment's `amount` must equal principal + interest + escrow; balances cannot go negative). The response is built by `PostingResponse.of` in `api/Dtos.java`.

The loss-mitigation flow is separate: `LossMitigationService.update` in `service/LossMitigationService.java` asks `LoanStatus.canMoveTo` (`domain/LoanStatus.java`) whether the move is legal, then changes the case and the loan's status in the same transaction. An illegal move is a 409 that lists the allowed next states. `LoanService.simulate` runs the same `compute` as `post` and saves nothing.

## Read the history in this order

There are no merged pull requests (`gh pr list --state merged` returns none) and no GitHub issues, so I grouped the 85 commits on `main` into phases. Hashes are first and last commit of each phase; the phase numbers follow commit order, which differs from the numbering in [INTERVIEW-NOTES.md](INTERVIEW-NOTES.md) (there the MCP server is phase 7 and the load test phase 8; in the log the load test comes first).

| Order | Phase | Commit range | What was built | What to notice |
|---|---|---|---|---|
| 1 | Scaffold and schema | `4bff97f`..`68ec8c0` | `.gitignore`, Gradle 8.14 and Spring Boot 3.5, Compose with Postgres, five Flyway tables, `scripts/check.sh` | `.gitignore` is the first commit on purpose; CHECK constraints and the partial unique index arrive here, before any Java rules |
| 2 | Domain and API | `1bc3620`..`c69124c` | `Amortization`, `LoanState`, `LoanStatus`, entities, the seed migration, services, controllers, Problem Details | Pure domain classes first, framework code after; the seed posts through `LoanState` |
| 3 | Tests | `e6892fe`..`10c4ac6` | Amortization, allocation and state-machine unit tests, Testcontainers integration tests, `scripts/count.sh` | Expected values were checked against a separate Python calculation |
| 4 | Dashboard | `760cac6`..`e41b235` | Vite, React and TypeScript, typed API client, theme, hand-written SVG chart, loans table and detail | No router and no chart library; the last commit fixes a clipped chart label |
| 5 | Docker and Kubernetes | `ffc6328`..`2394e69` | Multi-stage Dockerfile, Compose app service, ConfigMap, Postgres StatefulSet, Deployment, HPA, `scripts/kind-up.sh` | Startup probe, no CPU limit, and the script records the pod restart count |
| 6 | CI | `c4b0e6e`..`c4b0e6e` | GitHub Actions workflow for build and test, and the image build | One commit; CI does not run `check.sh` |
| 7 | Load test and results | `5ccb44a`..`de6d104` | k6 script, `render_results.py`, `scripts/results.sh`, recorded numbers, extended `check.sh` | `RESULTS.md` is generated from `results/`, never hand-edited |
| 8 | MCP server | `c58c84b`..`b032270` | Three read-only tools, stub-server tests, demo client and transcript, CI job | Only POST is the simulate endpoint; errors pass through `ToolError` |
| 9 | README | `ab24a2a`..`aaa73e1` | Screenshots, then the README one section per commit; the results table kept in sync by script | `aaa73e1` makes the README table generated too |
| 10 | Interview notes | `3d448f5`..`5f8d51b` | One notes commit per project phase | Hardest problem and likely questions for each phase |
| 11 | Wrap-up | `c77ce03`..`b341feb` | Later decisions, progress, morning summary, resume bullets, plan marked done | `PROGRESS.md` lists what is unverified |
| 12 | After the push (2026-11-04) | `8d0570b`..`000a1aa` | First green CI run recorded, backup branch note corrected | Phases 1 to 11 are dated 2026-11-02 |

## The 10 commits that matter most

| Short hash | Subject | Why it matters | File and function to open |
|---|---|---|---|
| `51a22d2` | feat(db): add payments table | A CHECK constraint makes the database reject any payment whose split does not add up | `src/main/resources/db/migration/V1__create_schema.sql`, table `payments` |
| `3fd916f` | feat(db): add loss_mitigation_cases table | A partial unique index allows one open case per loan, so concurrent requests cannot both open one | `V1__create_schema.sql`, index `loss_mitigation_one_open_case` |
| `f950f50` | feat(domain): add payment allocation rules | The core money rule, pure and shared by the API, simulate and the seed; `Terms.maturityDate` makes the last installment absorb rounding | `domain/LoanState.java`, `LoanState.apply` and `LoanState.installment` |
| `332812b` | feat(domain): add loss-mitigation state machine | A switch with no default means a new state without transitions fails to compile | `domain/LoanStatus.java`, `LoanStatus.next` |
| `9199901` | feat(api): add loan service | Posting and simulating share one `compute` method, so a simulation cannot disagree with a real post | `service/LoanService.java`, `LoanService.post`, `simulate`, `compute` |
| `75ab149` | test(domain): cover payment allocation edge cases | Contains the test that pays all 360 installments and ends at zero like the schedule, which backs the rounding claim | `src/test/java/com/loanledger/domain/LoanStateTest.java`, `payingEveryInstallmentEndsExactlyAtMaturityLikeTheSchedule` |
| `e6892fe` | test(domain): verify amortization against known values | Pins $1,896.20, $382,636.71 and the final $1,900.91, checked with a separate Python calculation | `AmortizationTest.java`, `scheduleFullyRepaysAndTotalInterestMatchesKnownValue` |
| `c58c84b` | feat(mcp): add read-only MCP server over the REST API | Calls the API instead of the database, and turns API errors into `ToolError` so the reason reaches the client (its message in `8ce7ed4` explains the bug a real client exposed) | `mcp-server/server.py`, `call_api` |
| `2394e69` | feat(k8s): add scripts/kind-up.sh and record the deploy | Waits for Postgres first because an earlier run restarted pods; records 2/2 ready and 0 restarts in `results/kind.txt` | `scripts/kind-up.sh` |
| `679f127` | feat(results): add scripts/results.sh and record real numbers | Where the p95, throughput and test counts come from; pairs with `1e319b1`, which renders `RESULTS.md` from raw files | `scripts/results.sh`, `scripts/loadtest.js` |

## 15-minute path

1. `README.md` (How it works): what is the project and what are its rules?
2. `src/main/java/com/loanledger/domain/LoanState.java`: how is a payment split, and what happens on a partial or excess payment?
3. `src/main/java/com/loanledger/domain/LoanStatus.java`: which status moves are legal?
4. `src/main/resources/db/migration/V1__create_schema.sql`: which rules does Postgres enforce on its own?
5. `src/main/java/com/loanledger/service/LoanService.java`: where do the transaction and the shared `compute` for post and simulate live?
6. `RESULTS.md`: which numbers can I quote and with what caveat?

## 60-minute path

1. `README.md` and `DECISIONS.md`: what was decided and why?
2. `src/main/resources/db/migration/V1__create_schema.sql`: what are the constraints and the partial unique index?
3. `src/main/java/db/seed/V2__seed_demo_data.java`: why is the seed a migration, and how does it tie balances out?
4. `src/main/java/com/loanledger/domain/Amortization.java`: how are the payment and the schedule rounded?
5. `src/main/java/com/loanledger/domain/LoanState.java`: how does `apply` work, and why does the last installment take the balance?
6. `src/main/java/com/loanledger/service/LoanService.java` and `LossMitigationService.java`: how do transactions, 422s and 409s arise?
7. `src/main/java/com/loanledger/api/ProblemHandler.java`: how does every error become Problem Details?
8. `src/test/java/com/loanledger/domain/LoanStateTest.java` and `AmortizationTest.java`: what proves the headline claims?
9. `src/test/java/com/loanledger/api/ApiIntegrationTest.java`: what does the real-Postgres test cover?
10. `web/src/BalanceChart.tsx` and `web/src/LoanDetail.tsx`: how does the dashboard avoid a chart library and show state without colour alone?
11. `Dockerfile`, `k8s/deployment.yaml`, `k8s/hpa.yaml`, `scripts/kind-up.sh`: what runs in the cluster and how is it measured?
12. `.github/workflows/ci.yml`: which jobs run on every push?
13. `mcp-server/server.py` and `mcp-server/transcript.txt`: how is it read-only, and what does a real session look like?
14. `scripts/check.sh`, `scripts/results.sh`, `scripts/render_results.py`: how are the numbers produced and kept honest?

## Numbers to remember

All from [RESULTS.md](../RESULTS.md). The raw files are in `results/`.

| Metric | Value | How it was measured |
|---|---|---|
| API endpoints (OpenAPI operations) | 11 | `scripts/count.sh`, counted from the exported OpenAPI JSON |
| Automated tests | 38 | `scripts/count.sh`, from Gradle's JUnit XML |
| Test failures | 0 | `scripts/count.sh` |
| MCP server tests | 4 | `scripts/count.sh` |
| `GET /api/v1/loans` p95 latency | 61.77 ms | `scripts/results.sh`, k6 |
| `GET /api/v1/loans` median latency | 19.64 ms | `scripts/results.sh`, k6 |
| Throughput | 788 requests/s | `scripts/results.sh`, k6 |
| Requests in the 30-second run | 23672 | `scripts/results.sh`, k6 |
| Failed requests | 0.00% | `scripts/results.sh`, k6 |
| kind deploy: health | UP | `scripts/kind-up.sh` |
| kind deploy: ready replicas | 2/2 | `scripts/kind-up.sh` |
| kind deploy: app pod restarts during rollout | 0 | `scripts/kind-up.sh` |
| kind deploy: seeded loans via the Service | 50 | `scripts/kind-up.sh` |
| kind deploy: Kubernetes version | v1.37.0 | `scripts/kind-up.sh` |

Load test setup: 20 virtual users for 30 seconds with no think time (`scripts/loadtest.js`), one page of 20 loans per request, against the Compose stack on one laptop.

From the README rounding section: a $300,000 loan at 6.5% over 360 months has a payment of $1,896.20, a last payment of $1,900.91 and total interest of $382,636.71 (the unrounded figure is $382,633.47).

## Weak spots an interviewer may probe

- **No authentication.** Anyone who can reach the API can post payments. Answer: Spring Security with OAuth2 and role-based access is the first thing I would add.
- **Double posting.** A retried successful POST creates a second payment. Answer: an idempotency key on the payment endpoint.
- **Delinquency is not automatic.** A loan becomes DELINQUENT only through a case. Answer: a daily job that checks due dates.
- **Simplified interest.** Monthly on the balance, with no daily accrual, late fees or escrow disbursements. Answer: it is a deliberate scope limit, listed as next steps.
- **A modification does not change the loan's terms.** It is a status only, and RESOLVED is terminal. Answer: tracked as a status in this version.
- **Overpayment above payoff gets a 422.** There is no unapplied-funds bucket (DECISIONS.md). Answer: rejecting is safer than silently keeping a borrower's money.
- **Optimistic locking only.** The loser of a concurrent write gets a 409 and retries. Answer: under heavy contention on one loan, `SELECT ... FOR UPDATE` would be better (README, design decisions).
- **Seed tied to the migration history.** Answer: `FLYWAY_LOCATIONS` lets a real deployment leave the seed out.
- **Load test is one laptop's baseline.** The load generator, app and database shared one machine inside a 4-CPU, 8 GB Colima VM that other containers were using (RESULTS.md). Answer: I call it a local baseline, not a capacity claim.
- **Fresh-clone runs shared this machine's caches.** They used a fresh clone but this machine's Gradle, npm and Docker caches (PROGRESS.md). Answer: they do not prove a first-ever download works.
- **Chart axis labels are small on a phone.** The SVG scales down as a whole.
- **Postgres runs in the cluster for the demo.** Answer: in production I would use a managed database.

The full questions and answers are in [INTERVIEW-NOTES.md](INTERVIEW-NOTES.md).

## Run it yourself

Prerequisite: Docker. Then:

```bash
docker compose up --build
```

Dashboard at <http://localhost:8080/>, Swagger UI at <http://localhost:8080/swagger-ui.html>, health at <http://localhost:8080/actuator/health>. Postgres is published on host port 5433.
