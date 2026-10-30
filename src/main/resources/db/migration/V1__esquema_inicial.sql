CREATE TABLE accounts (
    id          UUID PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    currency    CHAR(3)      NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE payments (
    id               UUID PRIMARY KEY,
    idempotency_key  VARCHAR(100) NOT NULL,
    request_hash     CHAR(64)     NOT NULL,
    payer_account_id UUID         NOT NULL REFERENCES accounts (id),
    payee_account_id UUID         NOT NULL REFERENCES accounts (id),
    amount_minor     BIGINT       NOT NULL CHECK (amount_minor > 0),
    currency         CHAR(3)      NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key)
);

-- Asientos inmutables: nunca se actualizan ni se borran
CREATE TABLE ledger_entries (
    id           UUID PRIMARY KEY,
    payment_id   UUID        NOT NULL REFERENCES payments (id),
    account_id   UUID        NOT NULL REFERENCES accounts (id),
    direction    VARCHAR(6)  NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount_minor BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency     CHAR(3)     NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_ledger_payment_direction UNIQUE (payment_id, direction)
);

CREATE INDEX idx_ledger_entries_account ON ledger_entries (account_id);
