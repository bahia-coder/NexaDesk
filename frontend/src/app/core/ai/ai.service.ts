import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { HANDLES_ERRORS } from '../http/api-error';
import {
  AiStatus,
  Article,
  AssistantAnswer,
  ChatTurn,
  Classification,
  SimilarContent,
} from './ai.models';

/**
 * AI endpoints. Suggestions are optional help: a failure is silent (the form works without them), so errors are
 * never shown as toasts.
 */
@Injectable({ providedIn: 'root' })
export class AiService {
  private readonly http = inject(HttpClient);
  private readonly quiet = { context: new HttpContext().set(HANDLES_ERRORS, true) };

  classify(title: string, description: string): Observable<Classification> {
    return this.http.post<Classification>('/api/ai/classify', { title, description }, this.quiet);
  }

  similar(title: string, description: string): Observable<SimilarContent> {
    return this.http.post<SimilarContent>('/api/ai/similar', { title, description }, this.quiet);
  }

  status(): Observable<AiStatus> {
    return this.http.get<AiStatus>('/api/ai/status', this.quiet);
  }

  /** The question with the previous turns (10 at most), so that a follow-up keeps its context. */
  assistant(question: string, history: ChatTurn[]): Observable<AssistantAnswer> {
    return this.http.post<AssistantAnswer>('/api/ai/assistant', { question, history }, this.quiet);
  }

  article(id: number): Observable<Article> {
    return this.http.get<Article>(`/api/knowledge/${id}`, this.quiet);
  }

  markHelpful(id: number): Observable<{ helpfulCount: number; helpedByMe: boolean }> {
    return this.http.post<{ helpfulCount: number; helpedByMe: boolean }>(
      `/api/knowledge/${id}/helpful`,
      null,
      this.quiet,
    );
  }
}
