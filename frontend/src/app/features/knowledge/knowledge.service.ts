import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { HANDLES_ERRORS } from '../../core/http/api-error';
import { Page } from '../../core/models/page.models';
import {
  ArticleFields,
  ArticleFilter,
  ArticleSummary,
  CategoryCount,
  KnowledgeArticle,
} from './knowledge.models';

/** Knowledge base: search, reading, drafts, publication, and links with tickets. */
@Injectable({ providedIn: 'root' })
export class KnowledgeService {
  private readonly http = inject(HttpClient);
  /** Errors shown by the calling form or action. */
  private readonly own = { context: new HttpContext().set(HANDLES_ERRORS, true) };

  list(filter: ArticleFilter, page: number, size: number): Observable<Page<ArticleSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [key, value] of Object.entries(filter)) {
      if (value) {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<Page<ArticleSummary>>('/api/knowledge', { params });
  }

  categoryCounts(): Observable<CategoryCount[]> {
    return this.http.get<CategoryCount[]>('/api/knowledge/categories');
  }

  /** Counts a view when the article is published. */
  get(id: number): Observable<KnowledgeArticle> {
    return this.http.get<KnowledgeArticle>(`/api/knowledge/${id}`);
  }

  create(fields: ArticleFields): Observable<KnowledgeArticle> {
    return this.http.post<KnowledgeArticle>('/api/knowledge', fields, this.own);
  }

  update(id: number, fields: ArticleFields, version: number): Observable<KnowledgeArticle> {
    return this.http.put<KnowledgeArticle>(
      `/api/knowledge/${id}`,
      { ...fields, version },
      this.own,
    );
  }

  publish(id: number): Observable<KnowledgeArticle> {
    return this.http.post<KnowledgeArticle>(`/api/knowledge/${id}/publish`, null, this.own);
  }

  archive(id: number): Observable<KnowledgeArticle> {
    return this.http.post<KnowledgeArticle>(`/api/knowledge/${id}/archive`, null, this.own);
  }

  markHelpful(id: number): Observable<{ helpfulCount: number; helpedByMe: boolean }> {
    return this.http.post<{ helpfulCount: number; helpedByMe: boolean }>(
      `/api/knowledge/${id}/helpful`,
      null,
      this.own,
    );
  }

  /** A draft pre-filled from a resolved ticket (description → symptoms, resolution → solution). */
  fromTicket(ticketId: number): Observable<KnowledgeArticle> {
    return this.http.post<KnowledgeArticle>(
      `/api/knowledge/from-ticket/${ticketId}`,
      null,
      this.own,
    );
  }

  ticketArticles(ticketId: number): Observable<ArticleSummary[]> {
    return this.http.get<ArticleSummary[]>(`/api/tickets/${ticketId}/knowledge-links`);
  }

  setTicketArticles(ticketId: number, articleIds: number[]): Observable<ArticleSummary[]> {
    return this.http.put<ArticleSummary[]>(
      `/api/tickets/${ticketId}/knowledge-links`,
      { articleIds },
      this.own,
    );
  }
}
