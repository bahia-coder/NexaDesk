import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { BackendStatus, HEALTH_CHECK_TIMEOUT_MS, SystemHealthService } from './system-health.service';

describe('SystemHealthService', () => {
  let service: SystemHealthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SystemHealthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function check(): BackendStatus[] {
    const results: BackendStatus[] = [];
    service.checkBackend().subscribe((s) => results.push(s));
    return results;
  }

  it('reports UP when the health endpoint is UP', () => {
    const results = check();
    http.expectOne('/actuator/health').flush({ status: 'UP' });
    expect(results).toEqual(['UP']);
  });

  it('reports DOWN when the backend answers with another status', () => {
    const results = check();
    http.expectOne('/actuator/health').flush({ status: 'OUT_OF_SERVICE' });
    expect(results).toEqual(['DOWN']);
  });

  it('reports UNREACHABLE on network or server error', () => {
    const results = check();
    http.expectOne('/actuator/health').flush(null, { status: 504, statusText: 'Gateway Timeout' });
    expect(results).toEqual(['UNREACHABLE']);
  });

  it('reports UNREACHABLE when the backend does not answer in time', () => {
    vi.useFakeTimers();
    try {
      const results = check();
      const req = http.expectOne('/actuator/health');
      vi.advanceTimersByTime(HEALTH_CHECK_TIMEOUT_MS);
      expect(results).toEqual(['UNREACHABLE']);
      expect(req.cancelled).toBe(true);
    } finally {
      vi.useRealTimers();
    }
  });
});
