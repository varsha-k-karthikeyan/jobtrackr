-- Demo user so the API can be exercised before auth exists (Stage 6).
-- password_hash is a placeholder, not a real BCrypt hash, so nobody can log in as this user.
INSERT INTO users (id, email, password_hash, full_name)
VALUES (1, 'demo@jobtrackr.dev', 'PLACEHOLDER_NOT_A_REAL_HASH', 'Demo User');

-- An explicit id does not advance the BIGSERIAL sequence; move it past 1 so the next insert gets id 2.
SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT MAX(id) FROM users));
