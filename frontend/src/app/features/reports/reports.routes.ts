import { Routes } from '@angular/router';

import { provideTimelineChart } from '../dashboard/timeline-chart.component';
import { ReportsPage } from './reports.page';

export const REPORT_ROUTES: Routes = [
  {
    path: '',
    component: ReportsPage,
    providers: [provideTimelineChart()],
    title: 'NexaDesk · Rapports',
  },
];
