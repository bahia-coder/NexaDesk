import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AdminDashboard,
  EmployeeDashboard,
  PeriodDays,
  TechnicianDashboard,
} from './dashboard.models';

/** Every figure is computed by the API (aggregated SQL); the screens only display them. */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);

  admin(days: PeriodDays): Observable<AdminDashboard> {
    return this.http.get<AdminDashboard>('/api/dashboard/admin', { params: { days } });
  }

  technician(): Observable<TechnicianDashboard> {
    return this.http.get<TechnicianDashboard>('/api/dashboard/technician');
  }

  employee(): Observable<EmployeeDashboard> {
    return this.http.get<EmployeeDashboard>('/api/dashboard/employee');
  }
}
