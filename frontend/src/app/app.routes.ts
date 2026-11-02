import { Routes } from '@angular/router';

/**
 * Development-only pages. `ngDevMode` is replaced by `false` at production build time, so these routes and their
 * code are removed from the production bundle (isDevMode(), a runtime call, would only hide them).
 */
const devRoutes: Routes =
  typeof ngDevMode === 'undefined' || ngDevMode
    ? [
        {
          path: 'dev/design-system',
          loadComponent: () =>
            import('./features/dev/design-system.page').then((m) => m.DesignSystemPage),
          title: 'NexaDesk · Design system',
        },
      ]
    : [];

export const routes: Routes = [
  ...devRoutes,
  {
    // Phase 2 setup check: replaced by the login page and the authenticated shell in the next steps.
    path: '',
    loadComponent: () =>
      import('./features/system-status/system-status.page').then((m) => m.SystemStatusPage),
    title: 'NexaDesk · État du système',
  },
  { path: '**', redirectTo: '' },
];
