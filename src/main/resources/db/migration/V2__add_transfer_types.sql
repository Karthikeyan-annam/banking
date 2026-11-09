-- ═══════════════════════════════════════════════════════════════════════════
-- V2__add_transfer_types.sql
-- Flyway migration: Add TRANSFER_OUT and TRANSFER_IN to transaction_type enum
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TYPE transaction_type ADD VALUE IF NOT EXISTS 'TRANSFER_OUT';
ALTER TYPE transaction_type ADD VALUE IF NOT EXISTS 'TRANSFER_IN';
