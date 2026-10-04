ALTER TABLE payment
    ADD COLUMN provider_token     VARCHAR(100),
    ADD COLUMN commission_percent NUMERIC(5,2)  NOT NULL DEFAULT 0,
    ADD COLUMN commission_amount  NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN net_amount         NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN paid_at            TIMESTAMPTZ;