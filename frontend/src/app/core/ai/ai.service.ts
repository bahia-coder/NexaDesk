import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { HANDLES_ERRORS } from '../http/api-error';
import { Classification, SimilarContent } from './ai.models';

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
}
