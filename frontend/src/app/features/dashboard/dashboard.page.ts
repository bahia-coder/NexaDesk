import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';
import { AdminDashboardComponent } from './admin-dashboard.component';
import { EmployeeDashboardComponent } from './employee-dashboard.component';
import { TechnicianDashboardComponent } from './technician-dashboard.component';

/** One route, three dashboards: the signed-in user's role picks which one (each calls its own endpoint). */
@Component({
  selector: 'nx-dashboard-page',
  imports: [AdminDashboardComponent, TechnicianDashboardComponent, EmployeeDashboardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @switch (auth.role()) {
      @case ('ADMIN') {
        <nx-admin-dashboard [days]="days()" />
      }
      @case ('TECHNICIAN') {
        <nx-technician-dashboard />
      }
      @case ('EMPLOYEE') {
        <nx-employee-dashboard />
      }
    }
  `,
})
export class DashboardPage {
  protected readonly auth = inject(AuthService);
  /** ?days= of the admin period selector. */
  readonly days = input<string>();
}
