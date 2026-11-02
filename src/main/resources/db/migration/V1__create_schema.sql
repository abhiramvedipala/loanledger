CREATE TABLE borrowers (
    id         BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE SEQUENCE loan_number_seq;

CREATE TABLE loans (
    id               BIGSERIAL PRIMARY KEY,
    loan_number      VARCHAR(20)    NOT NULL UNIQUE DEFAULT 'LL-' || lpad(nextval('loan_number_seq')::text, 6, '0'),
    borrower_id      BIGINT         NOT NULL REFERENCES borrowers (id),
    principal        NUMERIC(14, 2) NOT NULL CHECK (principal > 0),
    annual_rate      NUMERIC(6, 3)  NOT NULL CHECK (annual_rate >= 0 AND annual_rate <= 30), -- percent, 6.500 = 6.5%
    term_months      INT            NOT NULL CHECK (term_months BETWEEN 1 AND 600),
    origination_date DATE           NOT NULL,
    status           VARCHAR(20)    NOT NULL CHECK (status IN ('CURRENT', 'DELINQUENT', 'FORBEARANCE', 'MODIFICATION', 'RESOLVED')),
    monthly_payment  NUMERIC(14, 2) NOT NULL,                  -- principal and interest only
    current_balance  NUMERIC(14, 2) NOT NULL CHECK (current_balance >= 0),
    next_due_date    DATE           NOT NULL,
    interest_due     NUMERIC(14, 2) NOT NULL CHECK (interest_due >= 0),  -- unpaid part of the current installment
    principal_due    NUMERIC(14, 2) NOT NULL CHECK (principal_due >= 0),
    escrow_due       NUMERIC(14, 2) NOT NULL CHECK (escrow_due >= 0),
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX loans_status_idx ON loans (status);
CREATE INDEX loans_borrower_idx ON loans (borrower_id);
