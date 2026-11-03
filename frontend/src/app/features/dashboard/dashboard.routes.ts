import { Routes } from '@angular/router';

import { DashboardPage } from './dashboard.page';
import { provideTimelineChart } from './timeline-chart.component';

export const DASHBOARD_ROUTES: Routes = [
  {
    path: '',
    component: DashboardPage,
    providers: [provideTimelineChart()],
    title: 'NexaDesk · Tableau de bord',
  },
];
