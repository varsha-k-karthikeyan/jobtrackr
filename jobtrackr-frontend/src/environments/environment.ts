// Production build settings (`ng build`). `ng serve` and `ng build --configuration development`
// swap this file for environment.development.ts (fileReplacements in angular.json).
// Only public configuration belongs here: everything in this file ships to the browser.
export const environment = {
  production: true,
  // Relative on purpose: the server that serves the app (nginx in the Docker image, or the host's rewrite
  // rules) forwards /api/ to the backend, so the browser sees ONE origin. The httpOnly refresh cookie
  // (SameSite=Strict, Path=/api/v1/auth) then just works, with no CORS and no third-party cookie.
  apiBaseUrl: '/api/v1',
  // Development only: a hosted build sends real email, so there is no local inbox to point at
  mailInboxUrl: null as string | null,
};
