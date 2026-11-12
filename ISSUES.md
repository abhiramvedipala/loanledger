# Issues

Known problems and open questions for LoanLedger, taken only from the repository's own documents. There are no GitHub issues (`gh issue list --state all` is empty) and, on `main`, no `TODO` or `FIXME` comments (`git grep -e TODO -e FIXME main -- src web scripts` finds none), so every row below comes from a document. The Status column is the author's own reading of those documents, not a label they use.

| ID | Type | Summary | Status | Source |
|---|---|---|---|---|
| L1 | limit | No authentication: anyone who can reach the API can post payments | open (Spring Security with OAuth2 and roles is the first thing to add) | `README.md` (Known limits and next steps) |
| L2 | limit | Double posting is possible: a client that retries a successful POST creates a second payment | open (an idempotency key on the payment endpoint would fix it) | `README.md` (Known limits and next steps) |
| L3 | limit | Delinquency is not automatic: a loan becomes DELINQUENT only through a case, and nothing checks due dates on a schedule | open (a daily job would) | `README.md` (Known limits and next steps) |
| L4 | limit | Interest is simplified: monthly on the balance, with no daily accrual, late fees or escrow disbursements | open | `README.md` (Known limits and next steps) |
| L5 | limit | A modification does not change the loan's terms; it is tracked as a status only, and RESOLVED is terminal | open | `README.md` (Known limits and next steps) |
| L6 | limit | Load test numbers are one laptop's baseline: k6, the app and Postgres shared one machine, inside a 4-CPU, 8 GB Colima VM that other containers were using | open (treat as a local baseline, not a capacity claim) | `README.md` (Known limits), `RESULTS.md` (load test paragraph) |
| L7 | limit | On a phone, the chart's axis labels get small because the SVG scales down as a whole | open | `README.md` (Known limits and next steps) |
| L8 | limit | Postgres runs inside the cluster for the demo | open (mitigation: in production I would use a managed database) | `README.md` (Known limits and next steps) |
| L9 | limit | Under heavy contention on a single loan, optimistic locking makes the loser retry | open (`SELECT ... FOR UPDATE` would be better there) | `README.md` (Design decisions and trade-offs) |
| L10 | limit | Demo data is tied to the migration history because the seed is a Flyway migration | open (mitigation: `FLYWAY_LOCATIONS` lets a real deployment leave the seed out) | `README.md` (Design decisions and trade-offs) |
| L11 | limit | A payment above the payoff amount gets a 422, because there is no unapplied-funds bucket | by design (the prompt defines no unapplied-funds bucket) | `DECISIONS.md` |
| B1 | build note | `kind load` fails on Docker's attestation manifests | avoided from the first version of `scripts/kind-up.sh` (commit 2394e69), which builds with `--provenance=false` | `DECISIONS.md` |
| Q1 | open question | The fresh-clone runs of `scripts/check.sh` used this machine's Gradle, npm and Docker caches, so they do not prove a first-ever download works | open | `PROGRESS.md` (What is not verified or is limited) |
