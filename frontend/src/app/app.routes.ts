import { Routes } from '@angular/router';

import { authGuard, guestGuard, passwordChangedGuard, roleGuard } from './core/auth/auth.guards';

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
      {
        path: 'tickets',
        loadChildren: () =>
          import('./features/tickets/tickets.routes').then((m) => m.TICKET_ROUTES),
      },
      {
        path: 'interventions',
        canMatch: [roleGuard('TECHNICIAN', 'ADMIN')],
        loadComponent: () =>
          import('./features/interventions/my-interventions.page').then(
            (m) => m.MyInterventionsPage,
          ),
        title: 'NexaDesk · Mes interventions',
      },
      {
        path: 'equipment',
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/equipment/equipment.page').then((m) => m.EquipmentPage),
            title: 'NexaDesk · Équipements',
          },
          {
            path: ':id',
            loadComponent: () =>
              import('./features/equipment/equipment-detail.page').then(
                (m) => m.EquipmentDetailPage,
              ),
            title: 'NexaDesk · Équipement',
          },
        ],
      },
      {
        path: 'reports',
        canMatch: [roleGuard('TECHNICIAN', 'ADMIN')],
        loadChildren: () =>
          import('./features/reports/reports.routes').then((m) => m.REPORT_ROUTES),
      },
      {
        path: 'users',
        canMatch: [roleGuard('ADMIN')],
        children: [
          {
            path: '',
            loadComponent: () => import('./features/users/users.page').then((m) => m.UsersPage),
            title: 'NexaDesk · Utilisateurs',
          },
          {
            path: ':id',
            loadComponent: () =>
              import('./features/users/user-detail.page').then((m) => m.UserDetailPage),
            title: 'NexaDesk · Utilisateur',
          },
        ],
      },
      {
        path: 'settings',
        canMatch: [roleGuard('ADMIN')],
        loadComponent: () =>
          import('./features/settings/settings.page').then((m) => m.SettingsPage),
        title: 'NexaDesk · Paramètres',
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./features/notifications/notifications.page').then((m) => m.NotificationsPage),
        title: 'NexaDesk · Notifications',
      },
      {
        path: 'profile',
        loadComponent: () => import('./features/users/profile.page').then((m) => m.ProfilePage),
        title: 'NexaDesk · Mon profil',
      },
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
    ],
  },
  // Unknown address: home when signed in, sign-in page otherwise (the guards decide).
  { path: '**', redirectTo: '' },
];
