import { HttpClient, HttpContext, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { HANDLES_ERRORS } from '../../core/http/api-error';
import { ExportFormat, Report, ReportFilter } from './reports.models';

export interface ExportedFile {
  blob: Blob;
  fileName: string;
}

/** Activity report; the figures are computed by the API (aggregated SQL), the page only displays them. */
@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly http = inject(HttpClient);

  summary(filter: ReportFilter): Observable<Report> {
    return this.http.get<Report>('/api/reports/summary', { params: params(filter) });
  }

  /**
   * The same report as an Excel or PDF file (admins). Fetched with the access token like any API call — a plain
   * link could not send it — then handed to the browser as a download.
   */
  export(filter: ReportFilter, format: ExportFormat): Observable<ExportedFile> {
    return this.http
      .get(`/api/reports/export.${format}`, {
        params: params(filter),
        responseType: 'blob',
        observe: 'response',
        context: new HttpContext().set(HANDLES_ERRORS, true),
      })
      .pipe(
        map((response: HttpResponse<Blob>) => ({
          blob: response.body ?? new Blob(),
          fileName: fileName(response.headers.get('Content-Disposition')) ?? `rapport.${format}`,
        })),
      );
  }
}

function params(filter: ReportFilter): HttpParams {
  let result = new HttpParams();
  for (const [key, value] of Object.entries(filter)) {
    if (value !== undefined && value !== null && value !== '') {
      result = result.set(key, String(value));
    }
  }
  return result;
}

/** `attachment; filename="rapport-2026-08-05_2026-11-02.xlsx"` → the file name. */
export function fileName(disposition: string | null): string | null {
  const match = disposition?.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i);
  return match ? decodeURIComponent(match[1]) : null;
}
