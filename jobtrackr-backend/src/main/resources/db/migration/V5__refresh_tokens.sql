-- Refresh tokens: long-lived (14 days), opaque, one row per issued token.
-- Only a SHA-256 hash of the token is stored; the token itself lives in the user's httpOnly cookie.
-- Each refresh revokes the used token and issues a new one (rotation).

CREATE TABLE refresh_tokens (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  VARCHAR(64) NOT NULL UNIQUE,
  expires_at  TIMESTAMPTZ NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at  TIMESTAMPTZ
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

COMMENT ON TABLE refresh_tokens IS 'Issued refresh tokens (hash only). Rotated on every use; removed with the user.';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'Hex SHA-256 of the random token. A fast hash is fine here: the token has 256 bits of entropy, unlike a password. Deterministic, so the row can be looked up by it.';
COMMENT ON COLUMN refresh_tokens.expires_at IS 'After this instant the token is rejected, even if never used.';
COMMENT ON COLUMN refresh_tokens.revoked_at IS 'Set when the token is used (rotation) or on logout. Presenting a token revoked longer ago than the grace period signals theft and revokes all of that user''s tokens.';
