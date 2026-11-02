import { Routes } from '@angular/router';

export const TICKET_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./tickets.page').then((m) => m.TicketsPage),
    title: 'NexaDesk · Tickets',
  },
  {
    path: 'new',
    loadComponent: () => import('./create/ticket-create.page').then((m) => m.TicketCreatePage),
    title: 'NexaDesk · Nouveau ticket',
  },
  {
    path: ':id',
    loadComponent: () => import('./detail/ticket-detail.page').then((m) => m.TicketDetailPage),
    title: 'NexaDesk · Ticket',
  },
];
