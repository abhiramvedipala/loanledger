# LoanLedger

A cloud-native mortgage-servicing microservice: post payments, track escrow and move loans through loss mitigation, with a React dashboard on top.

[![CI](https://github.com/abhiramvedipala/loanledger/actions/workflows/ci.yml/badge.svg)](https://github.com/abhiramvedipala/loanledger/actions/workflows/ci.yml)

All data is synthetic. The seed is generated with a fixed random seed, and every email is `@example.com`.

## Problem

After a mortgage closes, a servicer collects the payments for the next 30 years. Each payment has to be split correctly between interest, principal and escrow; people pay late, pay part, or pay extra; and borrowers in hardship move through forbearance or modification under strict rules. A wrong split or an illegal status change is a real financial and compliance problem.

LoanLedger is a small version of that system for a servicing operations team. I built it to practice the parts of backend work that matter when the data is money: exact decimal arithmetic, invariants enforced in the database, concurrency safety and clear API errors.

## Demo

Loans table with status chips:

![Loans table](docs/loans-table.jpg)

Loan detail with the balance chart, payment form and loss-mitigation actions:

![Loan detail](docs/loan-detail.jpg)

## Architecture

```mermaid
flowchart LR
    Browser["React dashboard"] -->|"REST /api/v1"| App
    MCP["MCP server (read-only tools)"] -->|"REST /api/v1"| App
    K6["k6 load test"] -->|"GET /api/v1/loans"| App
    subgraph App["Spring Boot service"]
        Controllers --> Services
        Services --> Domain["Pure domain rules: Amortization, LoanState, LoanStatus"]
        Services --> Repositories["JPA repositories"]
    end
    Repositories --> Postgres[("PostgreSQL 16")]
    Flyway["Flyway migrations and seed"] --> Postgres
```

A request hits a controller, where Bean Validation rejects bad input as an RFC 7807 problem. The service loads the loan and hands its state to `LoanState`, a pure class that holds the money rules. The service then saves the new state and the payment in one transaction, and a `@Version` column turns a concurrent write into a 409. Spring serves the built dashboard from the same jar, so there is a single deployable, which runs under Compose or Kubernetes.

