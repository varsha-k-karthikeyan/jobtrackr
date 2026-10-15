import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth-service';
import { authInterceptor } from './core/interceptors/auth-interceptor';
import { errorInterceptor } from './core/interceptors/error-interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // withComponentInputBinding: route query params (returnUrl, list filters) arrive as component inputs
    provideRouter(routes, withComponentInputBinding()),
    // Order matters: requests pass auth -> error, responses come back error -> auth
    provideHttpClient(withFetch(), withInterceptors([authInterceptor, errorInterceptor])),
    // Non-blocking: the app renders immediately, the user's name appears when /me answers
    provideAppInitializer(() => inject(AuthService).restoreSession()),
  ],
};
