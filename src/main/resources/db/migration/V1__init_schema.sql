-- ═══════════════════════════════════════════════════════════════════════════
-- V1__init_schema.sql
-- Flyway migration: Initial schema for Secure Core Banking System
--
-- Naming conventions used throughout:
--   • Tables       : snake_case, plural (users, accounts, transactions)
--   • Primary Keys : UUID (gen_random_uuid()) — avoids int enumeration attacks
--   • Constraints  : chk_<table>_<column>
--   • Indexes      : idx_<table>_<column(s)>
--   • FK naming    : fk_<child>_<parent>
-- ═══════════════════════════════════════════════════════════════════════════

-- Enable pgcrypto extension for gen_random_uuid() (PostgreSQL < 14 compatibility)
-- On PostgreSQL 14+ you can use the built-in gen_random_uuid() without this extension.
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ─────────────────────────────────────────────────────────────────────────────
-- ENUM TYPES
-- Defining enums at the DB level provides an extra validation layer beyond
-- the application, and makes column semantics self-documenting.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TYPE user_role AS ENUM ('USER', 'ADMIN');

CREATE TYPE account_status AS ENUM ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'CLOSED');

CREATE TYPE account_type AS ENUM ('SAVINGS', 'CURRENT', 'FIXED_DEPOSIT');

-- PENDING  : Transfer received but not yet processed (async flows / future use)
-- COMPLETED: Both debit and credit legs have been booked successfully
-- FAILED   : Transaction was attempted but rolled back (e.g., insufficient funds)
-- REVERSED : A completed transaction that was subsequently reversed
CREATE TYPE transaction_status AS ENUM ('PENDING', 'COMPLETED', 'FAILED', 'REVERSED');

CREATE TYPE transaction_type AS ENUM ('DEBIT', 'CREDIT', 'TRANSFER', 'DEPOSIT', 'WITHDRAWAL');

-- ─────────────────────────────────────────────────────────────────────────────
-- TABLE: users
-- Stores authentication credentials and profile information.
-- Passwords are stored as BCrypt hashes; the plaintext is never persisted.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE users (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    email               VARCHAR(255)    NOT NULL,
    password_hash       VARCHAR(255)    NOT NULL,
    first_name          VARCHAR(100)    NOT NULL,
    last_name           VARCHAR(100)    NOT NULL,
    phone_number        VARCHAR(20),
    role                user_role       NOT NULL DEFAULT 'USER',
    is_enabled          BOOLEAN         NOT NULL DEFAULT TRUE,
    is_account_non_expired      BOOLEAN NOT NULL DEFAULT TRUE,
    is_account_non_locked       BOOLEAN NOT NULL DEFAULT TRUE,
    is_credentials_non_expired  BOOLEAN NOT NULL DEFAULT TRUE,
    -- Auditing columns: set at the DB level as a safety net;
    -- also managed by Spring Data JPA @CreatedDate / @LastModifiedDate
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_email CHECK (email ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    CONSTRAINT chk_users_phone CHECK (phone_number IS NULL OR phone_number ~ '^\+?[0-9\- ]{7,20}$')
);

-- Index on email: drives every authentication lookup (WHERE email = ?)
CREATE INDEX idx_users_email ON users (email);

-- ─────────────────────────────────────────────────────────────────────────────
-- TABLE: accounts
-- Each user may have one or more bank accounts.
-- Balance is stored as NUMERIC(19, 4) which maps exactly to Java BigDecimal;
-- using FLOAT/DOUBLE here would be a critical correctness bug.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE accounts (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL,
    -- 12-digit account number, unique, human-readable reference
    account_number  VARCHAR(20)     NOT NULL,
    account_type    account_type    NOT NULL DEFAULT 'SAVINGS',
    status          account_status  NOT NULL DEFAULT 'ACTIVE',
    -- NUMERIC(19,4): up to 999,999,999,999,999.9999 — sufficient for any retail bank
    balance         NUMERIC(19, 4)  NOT NULL DEFAULT 0.0000,
    currency        CHAR(3)         NOT NULL DEFAULT 'USD',  -- ISO 4217
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT uq_accounts_number UNIQUE (account_number),
    -- Non-negative balance: THIS IS THE MOST CRITICAL CONSTRAINT IN THE SCHEMA.
    -- Even if application logic has a bug, the DB will never permit a negative balance.
    CONSTRAINT chk_accounts_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT chk_accounts_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT fk_accounts_users
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT  -- Prevent orphaning accounts if a user is deleted
        ON UPDATE CASCADE
);

CREATE INDEX idx_accounts_user_id ON accounts (user_id);
CREATE INDEX idx_accounts_number  ON accounts (account_number);
CREATE INDEX idx_accounts_status  ON accounts (status);

-- ─────────────────────────────────────────────────────────────────────────────
-- TABLE: transactions
-- Immutable ledger of every financial event in the system.
-- Records are NEVER deleted or updated after insertion — only status may change
-- (from PENDING → COMPLETED or FAILED).
--
-- For a P2P transfer, TWO rows are written inside the same DB transaction:
--   1. sender_account → DEBIT  row
--   2. receiver_account → CREDIT row
-- Both share the same `reference_id` UUID, linking them as a logical pair.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE transactions (
    id                  UUID                NOT NULL DEFAULT gen_random_uuid(),
    -- reference_id groups the debit + credit legs of a single transfer
    reference_id        UUID                NOT NULL DEFAULT gen_random_uuid(),
    -- sender_account_id NULL is valid for external deposits (e.g., initial funding)
    sender_account_id   UUID,
    -- receiver_account_id NULL is valid for external withdrawals
    receiver_account_id UUID,
    transaction_type    transaction_type    NOT NULL,
    amount              NUMERIC(19, 4)      NOT NULL,
    currency            CHAR(3)             NOT NULL DEFAULT 'USD',
    status              transaction_status  NOT NULL DEFAULT 'PENDING',
    description         VARCHAR(500),
    -- IP address of the client who initiated the transfer (security audit trail)
    initiated_by_ip     VARCHAR(45),        -- IPv6 max length = 45 chars
    created_at          TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ         NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_transactions PRIMARY KEY (id),
    -- Amounts must always be positive; zero-value transactions are business-invalid
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transactions_currency CHECK (currency ~ '^[A-Z]{3}$'),
    -- A transfer must have at least one account involved
    CONSTRAINT chk_transactions_accounts
        CHECK (sender_account_id IS NOT NULL OR receiver_account_id IS NOT NULL),
    CONSTRAINT fk_transactions_sender
        FOREIGN KEY (sender_account_id) REFERENCES accounts (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_receiver
        FOREIGN KEY (receiver_account_id) REFERENCES accounts (id)
        ON DELETE RESTRICT
);

-- Primary query patterns:
CREATE INDEX idx_transactions_sender   ON transactions (sender_account_id,   created_at DESC);
CREATE INDEX idx_transactions_receiver ON transactions (receiver_account_id, created_at DESC);
CREATE INDEX idx_transactions_ref      ON transactions (reference_id);
CREATE INDEX idx_transactions_status   ON transactions (status);
CREATE INDEX idx_transactions_created  ON transactions (created_at DESC);

-- ─────────────────────────────────────────────────────────────────────────────
-- TRIGGER: auto-update updated_at columns
-- Keeps the updated_at column in sync without relying solely on the ORM layer.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_accounts_updated_at
    BEFORE UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_transactions_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- ─────────────────────────────────────────────────────────────────────────────
-- SEED DATA: Default Admin User
-- Password: "Admin@123" → BCrypt hash below (cost factor 12)
-- CHANGE THIS IMMEDIATELY in any real deployment.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO users (id, email, password_hash, first_name, last_name, role)
VALUES (
    gen_random_uuid(),
    'admin@banking.com',
    '$2a$12$mFkjGUEivqMmFUomjFzpyO4Y.1mj5OJRl5OC7vfWijDR0wuLkV1Ky', -- Admin@123
    'System',
    'Administrator',
    'ADMIN'
);
