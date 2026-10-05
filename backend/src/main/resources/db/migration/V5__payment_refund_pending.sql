DO $$
DECLARE c text;
BEGIN
SELECT conname INTO c FROM pg_constraint
WHERE conrelid = 'payment'::regclass AND contype = 'c'
       AND pg_get_constraintdef(oid) LIKE '%status%';
IF c IS NOT NULL THEN
        EXECUTE format('ALTER TABLE payment DROP CONSTRAINT %I', c);
END IF;
END $$;

ALTER TABLE payment ADD CONSTRAINT payment_status_check
    CHECK (status IN ('INITIATED','SUCCEEDED','FAILED','REFUND_PENDING','REFUNDED'));