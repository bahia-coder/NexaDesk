import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { HANDLES_ERRORS } from '../../core/http/api-error';
import { Page } from '../../core/models/page.models';
import { TicketStatus, TicketSummary } from '../../core/models/ticket.models';
import {
  Attachment,
  Category,
  Comment,
  CreateTicketRequest,
  EquipmentRef,
  Feedback,
  HistoryEntry,
  StatusChange,
  Technician,
  TicketDetail,
  TicketFilter,
} from './tickets.models';

/** Requests whose errors the caller reports itself (form message or action toast), not the global toast. */
const FORM = { context: new HttpContext().set(HANDLES_ERRORS, true) };

function filterParams(filter: TicketFilter): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(filter)) {
    if (Array.isArray(value)) {
      value.forEach((item) => (params = params.append(key, String(item))));
    } else if (value !== undefined && value !== null && value !== '') {
      params = params.set(key, String(value));
    }
  }
  return params;
}

/** Ticket endpoints of the API. Rules (who may do what) are enforced there; screens follow availableActions. */
@Injectable({ providedIn: 'root' })
export class TicketsService {
  private readonly http = inject(HttpClient);

  list(
    filter: TicketFilter,
    page: number,
    size: number,
    sort: string,
  ): Observable<Page<TicketSummary>> {
    const params = filterParams(filter).set('page', page).set('size', size).set('sort', sort);
    return this.http.get<Page<TicketSummary>>('/api/tickets', { params });
  }

  board(filter: TicketFilter): Observable<Partial<Record<TicketStatus, TicketSummary[]>>> {
    return this.http.get<Partial<Record<TicketStatus, TicketSummary[]>>>('/api/tickets/board', {
      params: filterParams(filter),
    });
  }

  get(id: number): Observable<TicketDetail> {
    return this.http.get<TicketDetail>(`/api/tickets/${id}`);
  }

  create(request: CreateTicketRequest): Observable<TicketDetail> {
    return this.http.post<TicketDetail>('/api/tickets', request, FORM);
  }

  update(
    id: number,
    title: string,
    description: string,
    equipmentId: number | null,
    version: number,
  ) {
    return this.http.put<TicketDetail>(
      `/api/tickets/${id}`,
      { title, description, equipmentId, version },
      FORM,
    );
  }

  reclassify(
    id: number,
    body: { categoryId: number; subcategoryId: number | null; priority: string; reason: string },
  ): Observable<TicketDetail> {
    return this.http.put<TicketDetail>(`/api/tickets/${id}/classification`, body, FORM);
  }

  take(id: number): Observable<TicketDetail> {
    return this.http.post<TicketDetail>(`/api/tickets/${id}/take`, null, FORM);
  }

  assign(id: number, technicianId: number, comment: string | null): Observable<TicketDetail> {
    return this.http.post<TicketDetail>(
      `/api/tickets/${id}/assign`,
      { technicianId, comment },
      FORM,
    );
  }

  changeStatus(id: number, change: StatusChange): Observable<TicketDetail> {
    return this.http.post<TicketDetail>(`/api/tickets/${id}/status`, change, FORM);
  }

  confirmResolution(id: number): Observable<TicketDetail> {
    return this.http.post<TicketDetail>(`/api/tickets/${id}/confirm-resolution`, null, FORM);
  }

  rejectResolution(id: number, reason: string): Observable<TicketDetail> {
    return this.http.post<TicketDetail>(`/api/tickets/${id}/reject-resolution`, { reason }, FORM);
  }

  reopen(id: number, reason: string): Observable<TicketDetail> {
    return this.http.post<TicketDetail>(`/api/tickets/${id}/reopen`, { reason }, FORM);
  }

  comments(id: number): Observable<Comment[]> {
    return this.http.get<Comment[]>(`/api/tickets/${id}/comments`);
  }

  addComment(id: number, body: string, internal: boolean): Observable<Comment> {
    return this.http.post<Comment>(`/api/tickets/${id}/comments`, { body, internal }, FORM);
  }

  history(id: number): Observable<HistoryEntry[]> {
    return this.http.get<HistoryEntry[]>(`/api/tickets/${id}/history`);
  }

  giveFeedback(id: number, rating: number, comment: string | null): Observable<Feedback> {
    return this.http.post<Feedback>(`/api/tickets/${id}/feedback`, { rating, comment }, FORM);
  }

  attachments(id: number): Observable<Attachment[]> {
    return this.http.get<Attachment[]>(`/api/tickets/${id}/attachments`);
  }

  upload(id: number, file: File): Observable<Attachment> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<Attachment>(`/api/tickets/${id}/attachments`, body, FORM);
  }

  /** Downloaded through HttpClient (the access token is a header, not a cookie). */
  download(id: number, attachmentId: number): Observable<Blob> {
    return this.http.get(`/api/tickets/${id}/attachments/${attachmentId}`, {
      responseType: 'blob',
    });
  }

  deleteAttachment(id: number, attachmentId: number): Observable<void> {
    return this.http.delete<void>(`/api/tickets/${id}/attachments/${attachmentId}`, FORM);
  }

  // ---- Reference data ---------------------------------------------------------------------------------

  categories(): Observable<Category[]> {
    return this.http.get<Category[]>('/api/categories');
  }

  technicians(categoryId?: number): Observable<Technician[]> {
    const params: Record<string, number> = categoryId ? { categoryId } : {};
    return this.http.get<Technician[]>('/api/users/technicians', { params });
  }

  selectableEquipment(): Observable<EquipmentRef[]> {
    return this.http.get<EquipmentRef[]>('/api/equipment/selectable');
  }
}
