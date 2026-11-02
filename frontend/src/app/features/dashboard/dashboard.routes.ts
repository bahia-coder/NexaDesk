import { Routes } from '@angular/router';
import {
  CategoryScale,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
} from 'chart.js';
import { provideCharts } from 'ng2-charts';

import { DashboardPage } from './dashboard.page';

/**
 * Lazy-loaded: Chart.js (only the parts the timeline uses) is downloaded with the dashboards, not with the
 * application shell.
 */
export const DASHBOARD_ROUTES: Routes = [
  {
    path: '',
    component: DashboardPage,
    providers: [
      provideCharts({
        registerables: [
          LineController,
          LineElement,
          PointElement,
          LinearScale,
          CategoryScale,
          Tooltip,
        ],
      }),
    ],
    title: 'NexaDesk · Tableau de bord',
  },
];
