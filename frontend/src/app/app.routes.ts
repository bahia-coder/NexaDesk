import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    // Phase 2 setup check: replaced by the login page and the authenticated shell in Phase 4.
    path: '',
    loadComponent: () =>
      import('./features/system-status/system-status.page').then((m) => m.SystemStatusPage),
    title: 'NexaDesk · État du système',
  },
  { path: '**', redirectTo: '' },
];
