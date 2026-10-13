CREATE TABLE users (
  id            BIGSERIAL PRIMARY KEY,
  email         VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  full_name     VARCHAR(120),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE applications (
  id             BIGSERIAL PRIMARY KEY,
  user_id        BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  company        VARCHAR(150) NOT NULL,
  role           VARCHAR(150) NOT NULL,
  status         VARCHAR(20)  NOT NULL CHECK (status IN ('APPLIED','INTERVIEW','OFFER','REJECTED')),
  position       INT NOT NULL DEFAULT 0,
  jd_url         TEXT,
  jd_text        TEXT,
  location       VARCHAR(100),
  salary_min_lpa NUMERIC(5,2),
  salary_max_lpa NUMERIC(5,2),
  source         VARCHAR(50),
  applied_on     DATE,
  follow_up_on   DATE,
  version        INT NOT NULL DEFAULT 0,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_app_user_status ON applications(user_id, status, position);
CREATE INDEX idx_app_follow_up   ON applications(follow_up_on) WHERE follow_up_on IS NOT NULL;

CREATE TABLE notes (
  id             BIGSERIAL PRIMARY KEY,
  application_id BIGINT NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
  body           TEXT NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE resumes (
  id           BIGSERIAL PRIMARY KEY,
  user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  file_name    VARCHAR(255) NOT NULL,
  content_text TEXT NOT NULL,
  is_active    BOOLEAN NOT NULL DEFAULT true,
  uploaded_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE match_results (
  id             BIGSERIAL PRIMARY KEY,
  user_id        BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  application_id BIGINT REFERENCES applications(id) ON DELETE SET NULL,
  resume_id      BIGINT NOT NULL REFERENCES resumes(id),
  score          INT NOT NULL CHECK (score BETWEEN 0 AND 100),
  matched_skills JSONB NOT NULL,
  missing_skills JSONB NOT NULL,
  summary        TEXT,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
