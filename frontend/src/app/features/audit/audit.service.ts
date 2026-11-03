import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Page } from '../../core/models/page.models';
import { AuditEntry, AuditFilter } from './audit.models';

/** Security and administration journal (admins only, enforced by the API). Most recent first. */
@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);

  search(filter: AuditFilter, page: number, size: number): Observable<Page<AuditEntry>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [key, value] of Object.entries(filter)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<Page<AuditEntry>>('/api/audit', { params });
  }
}
