-- One on/off switch per user for all reminder emails (morning digest and interview reminders). On by default,
-- also for existing accounts. The reminder job reads it on every run (every minute), so switching off stops
-- the emails from the next run on. Password-reset emails are not affected: the user asked for those.

ALTER TABLE users ADD COLUMN email_reminders_enabled BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN users.email_reminders_enabled IS 'Whether the user gets reminder emails (morning digest, interview starting soon). Set with PUT /me/email-reminders; read by the reminder job every minute.';
