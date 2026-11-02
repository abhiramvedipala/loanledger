# Decisions

One line each: decision + why.

- Docker runs on a new Colima VM (4 CPU, 8 GB), because this Mac has no Docker Desktop and the old Colima instance's files were gone.
- `PROMPT.md` stays out of the repo, because it is a private build brief and the project does not need it.
- Compose publishes Postgres on host port 5433 (`DB_PORT`), because a local Postgres already holds 5432 on this Mac.
- `check.sh` uses its own Compose project and ports 25433/28080, because other overnight projects share this Docker VM and their ports.
- `check.sh` was written after the phase 1-2 code, not before, because the brief that requires it replaced `PROMPT.md` mid-session.
- `annual_rate` is stored as a percent (6.500 = 6.5%), because that is how servicing documents quote rates.
- Extra money after a full installment goes to principal and a payment above the payoff amount gets a 422, because the prompt defines no unapplied-funds bucket.
- Seed is a Flyway Java migration, because Flyway's lock makes it run once even when several replicas start together.
- Seed loans all originate in December 2024 so their 12 months of payments tie to the balance; loans in loss mitigation or delinquency stop paying early, so they have fewer than 12 payments.
- Loan status and case status share one enum, and the loan follows its open case, because the prompt defines one state machine.
- Added `POST /loans/{id}/payments/simulate`, because the read-only MCP `simulate_payment` tool must call the REST API without saving anything.
- Integration tests use MockMvc on the full Spring context, because it needs no extra HTTP client for PATCH and still hits real Postgres.
