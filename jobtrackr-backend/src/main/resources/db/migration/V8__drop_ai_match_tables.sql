-- The AI resume-match feature was dropped from the project, so the two tables reserved for it in V1 (never
-- mapped to an entity, never written to) are removed. V1 and V3 are already applied and stay untouched.
-- match_results goes first: it references resumes. Their indexes and COMMENTs are dropped with them.
-- applications.jd_text stays: it is still useful as the pasted job description.

DROP TABLE match_results;
DROP TABLE resumes;
