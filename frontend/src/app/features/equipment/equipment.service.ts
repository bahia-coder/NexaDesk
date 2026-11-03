import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { HANDLES_ERRORS } from '../../core/http/api-error';
import { Page } from '../../core/models/page.models';
import { TicketSummary } from '../../core/models/ticket.models';
import {
  Assignment,
  Equipment,
  EquipmentFields,
  EquipmentFilter,
  EquipmentStatus,
} from './equipment.models';

/** Hardware inventory. Employees only get the devices they hold (enforced by the API). */
@Injectable({ providedIn: 'root' })
export class EquipmentService {
  private readonly http = inject(HttpClient);
  /** Errors shown by the calling form or action. */
  private readonly own = { context: new HttpContext().set(HANDLES_ERRORS, true) };

  list(filter: EquipmentFilter, page: number, size: number): Observable<Page<Equipment>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', 'inventoryCode,asc');
    for (const [key, value] of Object.entries(filter)) {
      if (value) {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<Page<Equipment>>('/api/equipment', { params });
  }

  get(id: number): Observable<Equipment> {
    return this.http.get<Equipment>(`/api/equipment/${id}`);
  }

  create(fields: EquipmentFields): Observable<Equipment> {
    return this.http.post<Equipment>('/api/equipment', fields, this.own);
  }

  update(id: number, fields: EquipmentFields): Observable<Equipment> {
    return this.http.put<Equipment>(`/api/equipment/${id}`, fields, this.own);
  }

  /** `userId` null returns the device to stock. */
  assign(id: number, userId: number | null): Observable<Equipment> {
    return this.http.post<Equipment>(`/api/equipment/${id}/assign`, { userId }, this.own);
  }

  changeStatus(id: number, status: EquipmentStatus): Observable<Equipment> {
    return this.http.put<Equipment>(`/api/equipment/${id}/status`, { status }, this.own);
  }

  retire(id: number): Observable<Equipment> {
    return this.http.post<Equipment>(`/api/equipment/${id}/retire`, null, this.own);
  }

  assignments(id: number): Observable<Assignment[]> {
    return this.http.get<Assignment[]>(`/api/equipment/${id}/assignments`);
  }

  tickets(id: number): Observable<TicketSummary[]> {
    return this.http.get<TicketSummary[]>(`/api/equipment/${id}/tickets`);
  }
}
