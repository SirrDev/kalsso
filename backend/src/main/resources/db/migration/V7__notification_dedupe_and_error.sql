ALTER TABLE notification
    ADD COLUMN dedupe_key VARCHAR(120),
    ADD COLUMN last_error VARCHAR(500);

CREATE UNIQUE INDEX uq_notification_dedupe_key
    ON notification (dedupe_key)
    WHERE dedupe_key IS NOT NULL;