import { HttpClient, HttpContext } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, firstValueFrom, map, of } from 'rxjs';

import { setCompanyTimeZone } from '../format/time';
import { HANDLES_ERRORS } from '../http/api-error';

interface ClientConfig {
  /** IANA zone of the company (backend APP_TIMEZONE). */
  timeZone: string;
}

/**
 * At start-up: reads the deployment settings the interface needs (the company time zone). Never fails: without
 * an answer, the default zone stays and the API's own errors show up on the first real request.
 */
export function loadClientConfig(): Promise<void> {
  return firstValueFrom(
    inject(HttpClient)
      .get<ClientConfig>('/api/client-config', {
        context: new HttpContext().set(HANDLES_ERRORS, true),
      })
      .pipe(
        map((config) => setCompanyTimeZone(config.timeZone)),
        catchError(() => of(undefined)),
      ),
  );
}
