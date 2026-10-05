ALTER TABLE payment
    ADD COLUMN refund_attempts        INT NOT NULL DEFAULT 0,
    ADD COLUMN refund_last_attempt_at TIMESTAMPTZ,
    ADD COLUMN refunded_at            TIMESTAMPTZ;

-- Accelere la recherche des remboursements a traiter
CREATE INDEX idx_payment_refund_pending ON payment (created_at) WHERE status = 'REFUND_PENDING';