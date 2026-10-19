// Development settings, used by `ng serve`. Must have the same shape as environment.ts.
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080/api/v1',
  // Local mail catcher (Mailpit, docker-compose.yml): where password-reset emails can be read during development
  mailInboxUrl: 'http://localhost:8025' as string | null,
};
