import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { HANDLES_ERRORS } from '../../core/http/api-error';
import {
  Intervention,
  InterventionRequest,
  MyInterventions,
  TicketInterventions,
} from './interventions.models';

/** Technicians' work log (support staff only; the API refuses employees). */
@Injectable({ providedIn: 'root' })
export class InterventionsService {
  private readonly http = inject(HttpClient);
  private readonly form = { context: new HttpContext().set(HANDLES_ERRORS, true) };

  forTicket(ticketId: number): Observable<TicketInterventions> {
    return this.http.get<TicketInterventions>(`/api/tickets/${ticketId}/interventions`);
  }

  add(ticketId: number, request: InterventionRequest): Observable<Intervention> {
    return this.http.post<Intervention>(
      `/api/tickets/${ticketId}/interventions`,
      request,
      this.form,
    );
  }

  update(ticketId: number, id: number, request: InterventionRequest): Observable<Intervention> {
    return this.http.put<Intervention>(
      `/api/tickets/${ticketId}/interventions/${id}`,
      request,
      this.form,
    );
  }

  /** Own interventions between two dates (yyyy-MM-dd, inclusive); the API defaults to the last 30 days. */
  mine(
    from: string | null,
    to: string | null,
    page: number,
    size: number,
  ): Observable<MyInterventions> {
    const params: Record<string, string | number> = { page, size, sort: 'createdAt,desc' };
    if (from) {
      params['from'] = from;
    }
    if (to) {
      params['to'] = to;
    }
    return this.http.get<MyInterventions>('/api/interventions/mine', { params });
  }
}
