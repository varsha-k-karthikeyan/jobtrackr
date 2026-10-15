-- Follow-ups are shown as in-app notifications instead of emails, so V6's "sent" column now records when
-- the user DISMISSED the notification. V6 is already applied and must not be edited, hence a rename here.
-- A follow-up is shown when follow_up_on <= today AND (reminder_dismissed_on IS NULL OR
-- reminder_dismissed_on < follow_up_on). Changing follow_up_on resets it to NULL (ApplicationService.update).

ALTER TABLE applications RENAME COLUMN reminder_sent_on TO reminder_dismissed_on;

COMMENT ON COLUMN applications.reminder_dismissed_on IS 'Day the user dismissed the follow-up notification (Asia/Kolkata date). NULL = not dismissed for the current follow_up_on. Reset when follow_up_on changes.';
