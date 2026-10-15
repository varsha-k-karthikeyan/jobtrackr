// Production build settings (`ng build`). `ng serve` and `ng build --configuration development`
// swap this file for environment.development.ts (fileReplacements in angular.json).
// Only public configuration belongs here: everything in this file ships to the browser.
export const environment = {
  production: true,
  // Set to the deployed backend's URL when the app is hosted
  apiBaseUrl: 'http://localhost:8080/api/v1',
};
