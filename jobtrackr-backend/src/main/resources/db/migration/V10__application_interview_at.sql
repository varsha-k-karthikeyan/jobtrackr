-- The scheduled interview for an application: date AND time, as an exact instant (TIMESTAMPTZ), so the
-- 30-minute reminder email fires at the right moment whatever the server's or the browser's time zone.
-- Optional: most cards never have one, or not yet.

ALTER TABLE applications ADD COLUMN interview_at TIMESTAMPTZ;

COMMENT ON COLUMN applications.interview_at IS 'Scheduled interview start (exact instant). Shown on the card; drives the morning digest and the 30-minute reminder email. NULL = none scheduled.';
