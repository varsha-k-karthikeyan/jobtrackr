-- Documentation only: no structure or data changes.
-- V1 and V2 are already applied, so they cannot be edited (Flyway checksums every applied file).
-- COMMENT ON stores the explanation in the database itself, where psql (\d+ applications),
-- IntelliJ's Database tool and pgAdmin all show it next to the column.

COMMENT ON TABLE users IS 'Accounts. One row per registered user; every other table hangs off this one.';
COMMENT ON COLUMN users.email IS 'Login name. Always stored lower-case by the application; the UNIQUE constraint is case-sensitive.';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hash, never the plain password. The seeded demo user (id 1) holds a placeholder and cannot log in.';

COMMENT ON TABLE applications IS 'Job applications = cards on the Kanban board. Removed automatically when the owning user is deleted.';
COMMENT ON COLUMN applications.user_id IS 'Owner. Every query in the application filters on this column.';
COMMENT ON COLUMN applications.status IS 'Board column: APPLIED, INTERVIEW, OFFER or REJECTED. Must match the Java enum ApplicationStatus.';
COMMENT ON COLUMN applications.position IS 'Zero-based order of the card inside its (user_id, status) column. Kept gap-free by ApplicationService: 0,1,2...';
COMMENT ON COLUMN applications.jd_url IS 'Link to the job description.';
COMMENT ON COLUMN applications.jd_text IS 'Pasted job description text; input for the AI resume match.';
COMMENT ON COLUMN applications.salary_min_lpa IS 'Lower end of the salary range in lakhs per annum (INR). NUMERIC(5,2): at most 999.99.';
COMMENT ON COLUMN applications.salary_max_lpa IS 'Upper end of the salary range in lakhs per annum (INR). NUMERIC(5,2): at most 999.99.';
COMMENT ON COLUMN applications.source IS 'Where the job was found, e.g. LinkedIn, referral.';
COMMENT ON COLUMN applications.follow_up_on IS 'Date to chase the application. Indexed (partially) for the reminder job.';
COMMENT ON COLUMN applications.version IS 'Optimistic-lock counter (JPA @Version). Incremented on every entity update; a stale value from the client gives HTTP 409.';
COMMENT ON COLUMN applications.updated_at IS 'Set by Hibernate (@UpdateTimestamp), not by a trigger. Bulk position shifts do not change it.';

COMMENT ON INDEX idx_app_user_status IS 'Serves the board query: one user''s cards ordered by status and position.';
COMMENT ON INDEX idx_app_follow_up IS 'Partial index: only rows that have a follow-up date, for the reminder lookup.';

COMMENT ON TABLE notes IS 'Free-text notes on an application. No user_id: the owner is the owner of the parent application. Removed with the application.';

COMMENT ON TABLE resumes IS 'Uploaded resumes as extracted text. Reserved for the AI match feature; no Java entity yet.';
COMMENT ON COLUMN resumes.is_active IS 'The resume used by default for matching.';

COMMENT ON TABLE match_results IS 'Outcome of comparing a resume with a job description. Reserved for the AI match feature; no Java entity yet.';
COMMENT ON COLUMN match_results.application_id IS 'Nullable and ON DELETE SET NULL: a match result survives the deletion of its application.';
COMMENT ON COLUMN match_results.score IS 'Match percentage, 0 to 100.';
COMMENT ON COLUMN match_results.matched_skills IS 'JSON array of skills found in both resume and job description.';
COMMENT ON COLUMN match_results.missing_skills IS 'JSON array of skills the job asks for that the resume lacks.';
