-- Follow-up reminder emails: remembers when a reminder went out, so each follow-up date is emailed once.
-- A row is due when follow_up_on <= today AND (reminder_sent_on IS NULL OR reminder_sent_on < follow_up_on).
-- Changing follow_up_on resets this to NULL (ApplicationService.update), which re-arms the reminder.

ALTER TABLE applications ADD COLUMN reminder_sent_on DATE;

COMMENT ON COLUMN applications.reminder_sent_on IS 'Day the follow-up reminder email was sent (Asia/Kolkata date). NULL = not sent for the current follow_up_on. Reset when follow_up_on changes.';
