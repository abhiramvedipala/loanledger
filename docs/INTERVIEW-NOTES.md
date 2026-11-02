# Interview notes

How I built LoanLedger, phase by phase, and what I'd say about each part. Current numbers are in [RESULTS.md](../RESULTS.md).

## Phase 1: Scaffold and schema

### What it built
- A Spring Boot 3.5 service on Java 21 with Gradle Kotlin DSL and Postgres 16 in Compose.
- Flyway migrations for borrowers, loans, payments, escrow accounts and loss-mitigation cases.
- A deterministic seed of 50 synthetic loans with up to 12 months of payments.
- `scripts/check.sh`, one command that defines "working".

### Key decisions
- **Why Flyway rather than Hibernate's `ddl-auto`?** The schema is versioned and reviewable, and it applies the same way everywhere. Hibernate only validates, so a mismatch fails at startup instead of silently altering a table.
- **Why `NUMERIC` rather than `double`?** Floating point can't represent most cents exactly. `NUMERIC(14,2)` with `BigDecimal` keeps every amount exact.
- **Why is the seed a migration?** Flyway's lock runs it once even when several replicas start together. A startup runner would need its own locking.
- **Why not load the seed from a SQL file?** The Java migration posts payments through the same rules as the API, so balances tie out. A hand-written SQL file could drift from those rules.

### Hardest problem
The app failed to start with `role "loanledger" does not exist`, even though Compose was healthy. A local Postgres already held port 5432, so the app was connecting to the wrong database. I found it with `lsof` and moved Compose to host port 5433, overridable through `DB_PORT`.

### Likely questions
1. **How do you make sure a payment's split adds up?** A check constraint on the table requires `amount = principal + interest + escrow`. The entity also derives the amount from the allocation, so the code can't build a bad row, and if it ever did, the database would reject it.
2. **What is the partial unique index for?** It indexes `loan_id` only where `resolved_at IS NULL`, which allows many closed cases but one open case per loan. A check in application code alone would have a race between the check and the insert.
3. **What happens if the seed runs twice?** It can't. Flyway records it in its history table as version 2. To start with no demo data, you leave `db/seed` out of `FLYWAY_LOCATIONS`.
4. **Why store interest due and principal due on the loan?** A partial payment leaves part of an installment unpaid, and that remainder has to live somewhere. Storing it keeps the next posting simple and avoids replaying payment history.
5. **Why have `check.sh` at all when there's CI?** It runs the same checks locally and adds things CI doesn't check, like bringing the full stack up and verifying that results match their raw files. It gives me one clear answer to "does it work?".

### Files to open
`src/main/resources/db/migration/V1__create_schema.sql`, `src/main/java/db/seed/V2__seed_demo_data.java`, `src/main/resources/application.yml`, `scripts/check.sh`

## Phase 2: Domain and API

### What it built
- Fixed-rate amortization with exact decimal arithmetic.
- Payment allocation of interest, then principal, then escrow, including partial payments and overpayments.
- The loss-mitigation state machine with illegal transitions rejected.
- Paginated `/api/v1` endpoints, RFC 7807 errors and Swagger UI.

### Key decisions
- **Why is `LoanState` a pure record?** The money rules are the part most likely to be wrong and most expensive if they are. With no framework, they are simple to test exhaustively, and the API, simulate and seed all share them.
- **Why optimistic locking rather than `SELECT FOR UPDATE`?** Two concurrent payments on one loan are rare, so I'd rather not hold row locks. The loser gets a 409 and retries. With heavy contention on a single row, I'd switch.
- **Why offset paging rather than keyset?** It's simple, it supports jumping to a page, and it's fine at this size. Keyset is the upgrade if the table reaches millions of rows.
- **Why 409 for an illegal transition rather than 400?** The request is well formed; it conflicts with the resource's current state. The body lists the allowed states so the client knows what to do.
- **Why is an overpayment above payoff rejected?** I have no unapplied-funds bucket, and silently keeping a borrower's money would be the worst option. A 422 forces a person to deal with it.

### Hardest problem
Rounding at the end of a loan. A rounded payment leaves a small residual at maturity, which would have created a tiny 361st installment. The fix was to pass the maturity date in `Terms` so the last installment takes the whole remaining balance, which is what the schedule does. A test now pays all 360 installments and checks that it ends at zero with the same total interest as the schedule.

### Likely questions
1. **Walk me through posting a payment.** Validation runs at the controller. The service loads the loan and escrow, checks the date and that the loan isn't paid off, and calls `LoanState.apply`. It then updates the loan and escrow and saves the payment in one transaction, and the `@Version` column catches a concurrent change.
2. **Why does simulate exist?** So a read-only caller can preview an allocation. It runs the same compute method as a real post and just doesn't save, so a simulation can't disagree with the real result.
3. **How did you avoid N+1 queries on the list?** The list needs the borrower's name, so the repository uses an `@EntityGraph` to fetch the borrower in a single join. Open-in-view is off, so an accidental lazy load in a controller fails loudly rather than quietly running more queries.
4. **Why not return entities from controllers?** That would tie the JSON contract to the schema and risk exposing fields by accident. Records make the contract explicit.
5. **What's missing for production?** Authentication first, then idempotency keys so a retried POST can't post twice, then daily interest, fees and escrow disbursements.

### Files to open
`src/main/java/com/loanledger/domain/LoanState.java`, `domain/Amortization.java`, `domain/LoanStatus.java`, `service/LoanService.java`, `api/ProblemHandler.java`

## Phase 3: Tests

### What it built
- Unit tests against the known $300,000 / 6.5% / 360-month schedule.
- Allocation edge cases and every legal transition, plus a sample of illegal ones.
- Testcontainers integration tests running the whole app on real Postgres.
- `scripts/count.sh`, which reads endpoint and test counts from real output.

### Key decisions
- **Why Testcontainers rather than H2?** The schema depends on Postgres features like partial unique indexes. H2 would either fail on them or pass while behaving differently from production.
- **Why MockMvc rather than a real HTTP client?** It runs the full Spring context and real database with less setup, and it handles PATCH without an extra client dependency. It skips the network layer, which Compose and `check.sh` cover separately.
- **How much coverage did you aim for?** I didn't chase a percentage. One test per behavior, weighted toward the money rules.

### Hardest problem
My total-interest test failed by $3.24 against a tolerance I had guessed. Rather than widening the tolerance, I calculated the schedule separately in Python. It matched the Java exactly at $382,636.71, which showed the code was right and my expectation was wrong: $382,633.47 is the unrounded figure. I pinned the exact values, so the test ended up stricter than before.

### Likely questions
1. **How do you know the amortization is right?** The expected values come from outside the code: the standard formula and a separate Python calculation with exact decimals. Testing code against its own output proves nothing.
2. **What's your most valuable test?** The one that pays all 360 installments through `LoanState` and compares the result to the schedule. It ties the two calculations together and caught the end-of-loan rounding issue.
3. **Why do the integration tests share one database?** Starting one container per test is slow. Each test creates its own loan with a unique borrower, so they don't interfere, and the seed test filters to seed rows.
4. **How do you test concurrency?** I rely on the `@Version` mechanism and the unique index rather than a timing-based test, which would be flaky. If I added one, I'd post two payments from a latch-synchronised pair of threads and assert one 409. That's a real gap.
5. **How do you know Testcontainers works in CI?** GitHub's Ubuntu runners have Docker. Locally I had to point it at the Colima socket, because it doesn't read Docker contexts.

### Files to open
`src/test/java/com/loanledger/domain/LoanStateTest.java`, `domain/AmortizationTest.java`, `api/ApiIntegrationTest.java`, `scripts/count.sh`

## Phase 4: React dashboard

### What it built
- A loans table with status chips, a filter and paging.
- A loan detail page with schedule, balance chart and payment history.
- A validated post-payment form and loss-mitigation actions.
- A build served by Spring under `/`.

### Key decisions
- **Why no router or chart library?** Two pages and one line chart don't justify the dependencies. Hash routing also means Spring needs no fallback route, and the chart is under 100 lines of SVG.
- **Why validate on both client and server?** Client validation gives fast feedback; the server is the actual rule. Server errors still appear in the form through the Problem Details message.
- **Why repeat the transition map in the client?** Only to decide which buttons to show. The server enforces the rules, so if the two ever disagreed the result would be a clear error, not a bad state.
- **Why icons on status chips?** So status isn't carried by colour alone, which matters for colour-blind users.

### Hardest problem
Nothing about the code was hard, but looking at it in a browser mattered: the chart's last year label was clipped, which no type-check would catch. I also tested the flows for real, posting a payment on a partly paid installment and confirming the split and the new due date against the API.

### Likely questions
1. **Why is the payment form keyed by balance?** The key resets the form after a successful post, so the default amount reflects the new amount due.
2. **How does a double click not post twice?** The button is disabled while the request is in flight. That only covers the UI; the API still needs an idempotency key, which is listed as a known limit.
3. **How is the chart accessible?** The SVG has an `aria-label` that summarises it, and the schedule table on the same page contains the same data as text.
4. **Why serve the UI from Spring rather than a CDN?** One deployable is simpler for a project this size, with no CORS to configure. At scale I'd serve static assets from a CDN.
5. **How do you handle dark mode?** With CSS variables and a separately chosen dark palette through `prefers-color-scheme`, rather than an automatic inversion.

### Files to open
`web/src/api.ts`, `web/src/App.tsx`, `web/src/LoanDetail.tsx`, `web/src/BalanceChart.tsx`

