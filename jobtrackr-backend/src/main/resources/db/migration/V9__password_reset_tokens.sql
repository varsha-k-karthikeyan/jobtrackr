-- "Forgot password" links. One row per link that has been emailed and not used yet.
-- Only a SHA-256 hash of the token is stored (like refresh_tokens): the token itself exists only in the email.
-- A row is deleted when its link is used, and every row of a user is deleted when a new link is requested,
-- so at most one link per user works at a time. Expired rows are removed by the nightly cleanup job.

CREATE TABLE password_reset_tokens (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  VARCHAR(64) NOT NULL UNIQUE,
  expires_at  TIMESTAMPTZ NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens(user_id);

COMMENT ON TABLE password_reset_tokens IS 'Unused "forgot password" links (hash only). Deleted on use, on a new request, or after expiry by the cleanup job.';
COMMENT ON COLUMN password_reset_tokens.token_hash IS 'Hex SHA-256 of the random token in the emailed link. The link itself is never stored.';
COMMENT ON COLUMN password_reset_tokens.expires_at IS 'The link stops working at this instant (jobtrackr.password-reset.token-ttl after created_at).';
COMMENT ON COLUMN password_reset_tokens.created_at IS 'Also used for the resend cooldown: no second email while a recent row exists.';
