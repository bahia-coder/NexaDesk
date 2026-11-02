import { Routes } from '@angular/router';

import { authGuard, guestGuard, passwordChangedGuard } from './core/auth/auth.guards';

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
        {
          // Shell preview without signing in: /dev/shell/design-system?role=ADMIN (EMPLOYEE, TECHNICIAN).
          path: 'dev/shell',
          loadComponent: () =>
            import('./layouts/app-shell/app-shell.component').then((m) => m.AppShellComponent),
          children: [
            {
              path: 'design-system',
              loadComponent: () =>
                import('./features/dev/design-system.page').then((m) => m.DesignSystemPage),
              title: 'NexaDesk · Design system',
            },
            { path: '', pathMatch: 'full', redirectTo: 'design-system' },
          ],
        },
      ]
    : [];

export const routes: Routes = [
  ...devRoutes,
  {
    path: 'login',
    canMatch: [guestGuard],
    loadComponent: () =>
      import('./layouts/auth-layout/auth-layout.component').then((m) => m.AuthLayoutComponent),
    children: [
      {
        path: '',
        loadComponent: () => import('./features/auth/login.page').then((m) => m.LoginPage),
        title: 'NexaDesk · Connexion',
      },
    ],
  },
  {
    path: 'change-password',
    canMatch: [authGuard],
    loadComponent: () =>
      import('./layouts/auth-layout/auth-layout.component').then((m) => m.AuthLayoutComponent),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/auth/change-password.page').then((m) => m.ChangePasswordPage),
        title: 'NexaDesk · Mot de passe',
      },
    ],
  },
  {
    // Every signed-in page lives in the shell; the sidebar lists the children that exist.
    path: '',
    canMatch: [authGuard],
    canActivateChild: [passwordChangedGuard],
    loadComponent: () =>
      import('./layouts/app-shell/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      {
        path: 'dashboard',
        loadChildren: () =>
          import('./features/dashboard/dashboard.routes').then((m) => m.DASHBOARD_ROUTES),
      },
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
    ],
  },
  // Unknown address: home when signed in, sign-in page otherwise (the guards decide).
  { path: '**', redirectTo: '' },
];
