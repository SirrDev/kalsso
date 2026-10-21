CREATE TABLE refresh_token (
                               id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               token_hash  VARCHAR(64) NOT NULL,
                               expires_at  TIMESTAMPTZ NOT NULL,
                               revoked_at  TIMESTAMPTZ,
                               created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_refresh_token_hash ON refresh_token (token_hash);
CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);