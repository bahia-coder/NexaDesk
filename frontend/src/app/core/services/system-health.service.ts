import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, timeout } from 'rxjs';

export type BackendStatus = 'UP' | 'DOWN' | 'UNREACHABLE';

export const HEALTH_CHECK_TIMEOUT_MS = 5000;

interface HealthResponse {
  status: string;
}

@Injectable({ providedIn: 'root' })
export class SystemHealthService {
  private readonly http = inject(HttpClient);

  /** Calls the public Spring Boot health endpoint (proxied to the API in dev, by Nginx in prod). */
  checkBackend(): Observable<BackendStatus> {
    return this.http.get<HealthResponse>('/actuator/health').pipe(
      timeout(HEALTH_CHECK_TIMEOUT_MS),
      map((res) => (res.status === 'UP' ? 'UP' : 'DOWN')),
      catchError(() => of<BackendStatus>('UNREACHABLE')),
    );
  }
}
