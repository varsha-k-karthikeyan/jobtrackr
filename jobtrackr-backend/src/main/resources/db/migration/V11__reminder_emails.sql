-- Reminder emails (notification/ReminderEmailService): a morning digest per user and a reminder shortly
-- before each interview. Both columns remember what was already sent, so a job that runs every minute
-- sends each email once, and catches up after the app was down.

ALTER TABLE users ADD COLUMN digest_sent_on DATE;

COMMENT ON COLUMN users.digest_sent_on IS 'Day (Asia/Kolkata date) the morning reminder digest was handled for this user: sent, or skipped because there was nothing to report. NULL = never.';

-- Stores the interview time the reminder was sent FOR, not a flag: when the user moves the interview,
-- interview_at no longer matches and the reminder is due again, with no reset code in the update path.
ALTER TABLE applications ADD COLUMN interview_reminder_sent_for TIMESTAMPTZ;

-- The reminder job looks for "interviews in the next 30 minutes" across all users every minute. Partial:
-- most cards have no interview, so they are left out of the index entirely.
CREATE INDEX idx_app_interview_at ON applications(interview_at) WHERE interview_at IS NOT NULL;

COMMENT ON COLUMN applications.interview_reminder_sent_for IS 'The interview_at value the "starts soon" email was sent for. A reminder is due when interview_at is within the lead time and differs from this value (NULL = never sent).';
