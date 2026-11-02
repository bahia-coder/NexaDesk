import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { HANDLES_ERRORS } from '../../core/http/api-error';
import { PriorityCode } from '../../core/models/ticket.models';
import { Category } from '../tickets/tickets.models';
import {
  BusinessCalendar,
  CategoryRequest,
  DayOfWeek,
  Holiday,
  MatrixEntry,
  PriorityItem,
  SlaPreview,
  SlaRule,
} from './settings.models';

/** Admin configuration: SLA rules and calendar, categories, priorities. Errors are shown by each form. */
@Injectable({ providedIn: 'root' })
export class SettingsService {
  private readonly http = inject(HttpClient);
  private readonly own = { context: new HttpContext().set(HANDLES_ERRORS, true) };

  rules(): Observable<SlaRule[]> {
    return this.http.get<SlaRule[]>('/api/sla/rules');
  }

  updateRule(
    id: number,
    body: Pick<
      SlaRule,
      'firstResponseMinutes' | 'resolutionMinutes' | 'businessHoursOnly' | 'warningThresholdPercent'
    >,
  ): Observable<SlaRule> {
    return this.http.put<SlaRule>(`/api/sla/rules/${id}`, body, this.own);
  }

  calendar(): Observable<BusinessCalendar> {
    return this.http.get<BusinessCalendar>('/api/sla/calendar');
  }

  updateCalendar(
    workStart: string,
    workEnd: string,
    workingDays: DayOfWeek[],
  ): Observable<BusinessCalendar> {
    return this.http.put<BusinessCalendar>(
      '/api/sla/calendar',
      { workStart, workEnd, workingDays },
      this.own,
    );
  }

  holidays(): Observable<Holiday[]> {
    return this.http.get<Holiday[]>('/api/sla/holidays');
  }

  addHoliday(day: string, label: string): Observable<Holiday> {
    return this.http.post<Holiday>('/api/sla/holidays', { day, label }, this.own);
  }

  deleteHoliday(id: number): Observable<void> {
    return this.http.delete<void>(`/api/sla/holidays/${id}`, this.own);
  }

  preview(priority: PriorityCode, from: string | null): Observable<SlaPreview> {
    const params: Record<string, string> = { priority };
    if (from) {
      params['from'] = from;
    }
    return this.http.get<SlaPreview>('/api/sla/preview', { params, ...this.own });
  }

  categories(): Observable<Category[]> {
    return this.http.get<Category[]>('/api/categories', { params: { includeInactive: true } });
  }

  createCategory(body: CategoryRequest): Observable<Category> {
    return this.http.post<Category>('/api/categories', body, this.own);
  }

  updateCategory(id: number, body: CategoryRequest): Observable<Category> {
    return this.http.put<Category>(`/api/categories/${id}`, body, this.own);
  }

  setCategoryActive(id: number, active: boolean): Observable<Category> {
    return this.http.post<Category>(
      `/api/categories/${id}/${active ? 'activate' : 'deactivate'}`,
      null,
      this.own,
    );
  }

  addSubcategory(categoryId: number, name: string): Observable<unknown> {
    return this.http.post(`/api/categories/${categoryId}/subcategories`, { name }, this.own);
  }

  renameSubcategory(id: number, name: string): Observable<unknown> {
    return this.http.put(`/api/subcategories/${id}`, { name }, this.own);
  }

  setSubcategoryActive(id: number, active: boolean): Observable<unknown> {
    return this.http.post(
      `/api/subcategories/${id}/${active ? 'activate' : 'deactivate'}`,
      null,
      this.own,
    );
  }

  priorities(): Observable<PriorityItem[]> {
    return this.http.get<PriorityItem[]>('/api/priorities');
  }

  matrix(): Observable<MatrixEntry[]> {
    return this.http.get<MatrixEntry[]>('/api/priorities/matrix');
  }

  updatePriority(id: number, label: string, color: string): Observable<PriorityItem> {
    return this.http.put<PriorityItem>(`/api/priorities/${id}`, { label, color }, this.own);
  }
}
