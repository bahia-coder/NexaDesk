import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding, withViewTransitions } from '@angular/router';

import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { authInterceptor, errorInterceptor } from './core/http/interceptors';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withComponentInputBinding(),
      // No animation on the first page: nothing to transition from, and it would delay the first paint.
      withViewTransitions({ skipInitialTransition: true }),
    ),
    // authInterceptor first: it sees the final outcome (after the 401 → refresh → retry cycle).
    provideHttpClient(withFetch(), withInterceptors([authInterceptor, errorInterceptor])),
    // A reload keeps the session: the refresh cookie is traded for a new access token before routing.
    provideAppInitializer(() => inject(AuthService).restoreSession()),
  ],
};
