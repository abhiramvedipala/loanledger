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

