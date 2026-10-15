-- Public demo account with sample data, so anyone browsing the repository can try the app.
--
--   email:    demo@jobtrackr.dev
--   password: Demo@1234
--
-- THIS ACCOUNT IS PUBLIC BY DESIGN. Its password is published in the README and in the frontend.
-- Never store anything private in it; register your own account for real use.
--
-- Dates are relative to CURRENT_DATE (the day this migration runs), so a fresh database always shows
-- recent applications, overdue follow-ups and follow-ups due today.

-- 1. Real login for the V2 demo user.
-- The value below is the BCrypt hash (cost 10, the default of the app's BCryptPasswordEncoder bean) of the
-- public demo password "Demo@1234". It is not a secret: the password itself is published.
UPDATE users
   SET password_hash = '$2a$10$iOGLje4JJYdfvT6VFHMD9.TlUq3IKn3.yB2.tptAMcz66vKEzR1L.'
 WHERE id = 1;

-- 2. Remove whatever the demo user had before (rows created while testing with the hardcoded user id).
-- Their notes go with them (ON DELETE CASCADE on notes.application_id).
DELETE FROM applications WHERE user_id = 1;

-- 3. Sample applications: 6 APPLIED, 4 INTERVIEW, 2 OFFER, 4 REJECTED.
-- position runs 0, 1, 2... inside each status column, as ApplicationService expects.
INSERT INTO applications
  (user_id, company, role, status, position, jd_url, jd_text, location,
   salary_min_lpa, salary_max_lpa, source, applied_on, follow_up_on)
VALUES
  -- APPLIED
  (1, 'Razorpay', 'Senior Frontend Engineer', 'APPLIED', 0,
   'https://razorpay.com/jobs/',
   'We are looking for a Senior Frontend Engineer to build the merchant dashboard.
- 5+ years building large single-page applications, Angular preferred
- Strong TypeScript and RxJS; experience with Angular Signals is a plus
- Experience with state management, lazy loading and performance budgets
- Work closely with Java/Spring Boot backend teams on REST API design
- Write unit tests with Jasmine/Jest and end-to-end tests with Cypress
- Mentor junior engineers and review code',
   'Bengaluru', 28.00, 36.00, 'LinkedIn', CURRENT_DATE - 3, CURRENT_DATE + 4),
  (1, 'Postman', 'Software Engineer II', 'APPLIED', 1,
   NULL, NULL, 'Remote', 25.00, 32.00, 'Company site', CURRENT_DATE - 5, CURRENT_DATE),
  (1, 'Darwinbox', 'Angular Developer', 'APPLIED', 2,
   NULL, NULL, 'Hyderabad', 16.00, 22.00, 'Naukri', CURRENT_DATE - 8, CURRENT_DATE - 2),
  (1, 'Chargebee', 'Full Stack Developer (Java + Angular)', 'APPLIED', 3,
   'https://www.chargebee.com/careers/',
   'Join the billing platform team as a full stack developer.
- 4-7 years of experience with Java 17+, Spring Boot and Spring Data JPA
- Angular 15+ with reactive forms, RxJS and Angular Material
- PostgreSQL: schema design, indexing and query tuning
- Experience with REST APIs, JWT-based authentication and OAuth2
- Familiarity with Docker and CI/CD pipelines
- Good communication skills; you will work with product and design daily',
   'Chennai', 20.00, 28.00, 'Referral', CURRENT_DATE - 11, NULL),
  (1, 'Groww', 'Frontend Engineer (Angular)', 'APPLIED', 4,
   NULL, NULL, 'Bengaluru', 22.00, 30.00, 'LinkedIn', CURRENT_DATE - 15, CURRENT_DATE + 2),
  (1, 'Infosys', 'Technology Lead (Angular)', 'APPLIED', 5,
   NULL, NULL, 'Pune', 14.00, 19.00, 'Naukri', CURRENT_DATE - 20, NULL),

  -- INTERVIEW
  (1, 'Freshworks', 'Senior Software Engineer (Frontend)', 'INTERVIEW', 0,
   'https://www.freshworks.com/company/careers/',
   'Freshworks is hiring a Senior Software Engineer for the Freshdesk agent workspace.
- 5+ years of frontend development with Angular or Ember
- Deep understanding of change detection, component architecture and accessibility (WCAG)
- Experience building design-system components shared across teams
- Comfortable with Node.js tooling, Webpack/esbuild and monorepos
- Exposure to Java microservices is a plus',
   'Chennai', 24.00, 32.00, 'Referral', CURRENT_DATE - 18, CURRENT_DATE + 1),
  (1, 'Zoho', 'Member Technical Staff (Web)', 'INTERVIEW', 1,
   NULL, NULL, 'Chennai', 15.00, 22.00, 'Company site', CURRENT_DATE - 24, CURRENT_DATE - 1),
  (1, 'PhonePe', 'Software Engineer II (Frontend)', 'INTERVIEW', 2,
   'https://www.phonepe.com/careers/',
   'Build the web experiences behind PhonePe Business.
- 4+ years with Angular and TypeScript in production
- Strong RxJS fundamentals: higher-order mapping, error handling, cancellation
- Experience securing SPAs: token handling, XSS and CSRF mitigations
- Working knowledge of Spring Boot and Spring Security on the backend
- Performance tuning: bundle size, lazy loading, OnPush and Signals',
   'Bengaluru', 26.00, 34.00, 'LinkedIn', CURRENT_DATE - 27, CURRENT_DATE),
  (1, 'Thoughtworks', 'Consultant, Full Stack (Java + Angular)', 'INTERVIEW', 3,
   NULL, NULL, 'Pune', 20.00, 27.00, 'Naukri', CURRENT_DATE - 30, CURRENT_DATE + 3),

  -- OFFER
  (1, 'Swiggy', 'Senior Frontend Engineer', 'OFFER', 0,
   NULL, NULL, 'Bengaluru', 30.00, 38.00, 'Referral', CURRENT_DATE - 36, CURRENT_DATE + 2),
  (1, 'Keka', 'Lead Angular Developer', 'OFFER', 1,
   NULL, NULL, 'Hyderabad', 22.00, 28.00, 'LinkedIn', CURRENT_DATE - 40, NULL),

  -- REJECTED
  (1, 'Flipkart', 'UI Engineer II', 'REJECTED', 0,
   NULL, NULL, 'Bengaluru', 28.00, 35.00, 'LinkedIn', CURRENT_DATE - 42, NULL),
  (1, 'Zerodha', 'Frontend Developer', 'REJECTED', 1,
   NULL, NULL, 'Remote', 18.00, 24.00, 'Other', CURRENT_DATE - 33, NULL),
  (1, 'TCS', 'Angular Lead', 'REJECTED', 2,
   NULL, NULL, 'Chennai', 12.00, 16.00, 'Naukri', CURRENT_DATE - 38, NULL),
  (1, 'Meesho', 'Software Development Engineer II', 'REJECTED', 3,
   NULL, NULL, 'Bengaluru', 24.00, 30.00, 'Company site', CURRENT_DATE - 28, NULL);

-- 4. Notes, looked up by company (each company appears once in the demo data).
-- created_at is backdated so the timelines read in a sensible order.
INSERT INTO notes (application_id, body, created_at)
SELECT a.id, n.body, now() - n.age
  FROM (VALUES
          ('Freshworks', 'Recruiter call scheduled for Thursday, 11 AM', INTERVAL '12 days'),
          ('Freshworks', 'Round 1 done: asked about RxJS switchMap vs mergeMap and OnPush change detection', INTERVAL '5 days'),
          ('PhonePe',    'Screening call: asked about RxJS and Spring Security', INTERVAL '9 days'),
          ('PhonePe',    'Machine coding round next: build a paginated table with search', INTERVAL '2 days'),
          ('Zoho',       'Take-home assignment submitted', INTERVAL '6 days'),
          ('Thoughtworks', 'Pair-programming round scheduled; brush up on TDD', INTERVAL '3 days'),
          ('Swiggy',     'Offer received: 34 LPA fixed plus ESOPs; decision due next week', INTERVAL '4 days'),
          ('Flipkart',   'Rejected after the system design round; feedback: go deeper on caching', INTERVAL '20 days')
       ) AS n(company, body, age)
  JOIN applications a ON a.company = n.company AND a.user_id = 1;

-- 5. Keep the id sequences ahead of the highest id, so the next registration or insert gets a fresh id.
SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT MAX(id) FROM users));
SELECT setval(pg_get_serial_sequence('applications', 'id'), (SELECT MAX(id) FROM applications));
SELECT setval(pg_get_serial_sequence('notes', 'id'), (SELECT MAX(id) FROM notes));

-- 6. V3's comment on users.password_hash said the demo user cannot log in; that is no longer true.
COMMENT ON COLUMN users.password_hash IS 'BCrypt hash, never the plain password. The demo user (id 1) has the hash of the public password Demo@1234 (set by V4).';
