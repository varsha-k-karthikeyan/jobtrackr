// Commit message rules, enforced by the .husky/commit-msg hook on every `git commit`.
// Format (Conventional Commits, the same convention Angular uses):
//
//   <type>(<scope>): <subject>
//
//   feat(application): add follow-up reminder endpoint
//   fix(auth): reject tokens signed with an old secret
//
// See CONTRIBUTING.md for the full guide.
module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    // What kind of change this is
    'type-enum': [2, 'always', [
      'feat',     // new behaviour visible to an API client or user
      'fix',      // bug fix
      'docs',     // documentation or comments only
      'refactor', // code change that neither fixes a bug nor adds behaviour
      'test',     // adding or correcting tests
      'perf',     // performance improvement
      'build',    // Maven, npm, dependencies
      'ci',       // pipeline configuration
      'chore',    // repository housekeeping
      'style',    // formatting only
      'revert',   // reverts an earlier commit
    ]],
    // Which part of the monorepo is touched. Optional, but when present it must be one of these.
    'scope-enum': [2, 'always', [
      'application', 'note', 'user', 'auth', 'stats', 'notification', 'common', // backend feature packages
      'db',       // Flyway migrations
      'config',   // application.yml, pom.xml settings
      'docker',   // docker-compose.yml
      'frontend', // Angular app (later)
      'deps',     // dependency upgrades
      'docs',
      'repo',     // root tooling: git hooks, .gitignore, commitlint
    ]],
    'header-max-length': [2, 'always', 100],
  },
};
