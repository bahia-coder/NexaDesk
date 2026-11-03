import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, computed, input, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { AuthService } from '../../core/auth/auth.service';
import { Role } from '../../core/models/user.models';
import { TimelineChartComponent } from '../dashboard/timeline-chart.component';
import { Report, reportPeriodError } from './reports.models';
import { ReportsPage } from './reports.page';
import { fileName } from './reports.service';

@Component({ selector: 'nx-timeline-chart', template: '' })
class TimelineChartStub {
  readonly points = input<unknown>();
  readonly granularity = input<unknown>();
}

const REPORT: Report = {
  scope: {
    from: '2026-08-05',
    to: '2026-11-02',
    categoryId: null,
    categoryName: null,
    technicianId: null,
    technicianName: null,
  },
  summary: {
    created: 37,
    resolved: 21,
    resolutionRate: 56.8,
    avgResolutionHours: 40.2,
    sla: { firstResponseRate: 89.2, resolutionRate: 71.4, resolvedMeasured: 21 },
  },
  byCategory: [{ key: '1', label: 'Réseau & VPN', color: null, count: 9 }],
  byPriority: [{ key: 'HIGH', label: 'Haute', color: '#ea580c', count: 8 }],
  byTechnician: [
    {
      id: 2,
      fullName: 'Karim Belhadj',
      avatarColor: '#4f46e5',
      openAssigned: 4,
      resolved: 12,
      avgResolutionHours: 30.5,
      slaRate: 83.3,
      avgRating: 4.6,
      minutesLogged: 640,
    },
  ],
  monthly: [{ bucket: '2026-08-01', created: 10, resolved: 6 }],
  satisfaction: { average: 4.7, count: 6, distribution: [0, 0, 0, 2, 4] },
  generatedAt: '2026-11-03T07:00:00Z',
};

describe('Report rules', () => {
  it('checks the period like the API', () => {
    expect(reportPeriodError('2026-08-05', '2026-11-02')).toBeNull();
    expect(reportPeriodError('2026-11-02', '2026-08-05')).toContain('précéder');
    expect(reportPeriodError('2024-11-01', '2026-11-02')).toContain('deux ans');
    expect(reportPeriodError(null, '2026-11-02')).toBeNull();
  });

  it('reads the export file name sent by the API', () => {
    expect(fileName('attachment; filename="nexadesk-rapport-2026-08-05_2026-11-02.xlsx"')).toBe(
      'nexadesk-rapport-2026-08-05_2026-11-02.xlsx',
    );
    expect(fileName(null)).toBeNull();
  });
});

describe('Reports page', () => {
  let http: HttpTestingController;
  const role = signal<Role>('ADMIN');

  beforeEach(() => {
    role.set('ADMIN');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'reports', component: ReportsPage }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { role: computed(() => role()) } },
      ],
    });
    TestBed.overrideComponent(ReportsPage, {
      remove: { imports: [TimelineChartComponent] },
      add: { imports: [TimelineChartStub] },
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(url: string) {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    http.expectOne('/api/categories').flush([]);
    return harness;
  }

  it('applies the URL filters and exports the same report', async () => {
    const harness = await open('/reports?from=2026-09-01&to=2026-10-31&technicianId=2');
    http
      .expectOne((r) => r.url === '/api/users')
      .flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    const summary = http.expectOne((r) => r.url === '/api/reports/summary');
    expect(summary.request.params.get('from')).toBe('2026-09-01');
    expect(summary.request.params.get('technicianId')).toBe('2');
    summary.flush({
      ...REPORT,
      scope: {
        ...REPORT.scope,
        from: '2026-09-01',
        to: '2026-10-31',
        technicianId: 2,
        technicianName: 'Karim Belhadj',
      },
    });
    harness.detectChanges();
    await harness.fixture.whenStable();
    const el = harness.routeNativeElement as HTMLElement;
    expect(el.querySelector('.page__header .muted')?.textContent).toContain(
      'Du 01/09/2026 au 31/10/2026',
    );
    expect(el.textContent).toContain('Karim Belhadj');
    expect(el.textContent).toContain('56,8 %');

    const createObjectURL = vi.fn(() => 'blob:report');
    const revokeObjectURL = vi.fn();
    Object.assign(URL, { createObjectURL, revokeObjectURL });
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);
    Array.from(el.querySelectorAll<HTMLButtonElement>('.exports button'))
      .find((b) => b.textContent?.includes('Excel'))!
      .click();
    const file = http.expectOne((r) => r.url === '/api/reports/export.xlsx');
    expect(file.request.params.get('technicianId')).toBe('2');
    file.flush(new Blob(['xlsx']), {
      headers: { 'Content-Disposition': 'attachment; filename="nexadesk-rapport.xlsx"' },
    });
    expect(createObjectURL).toHaveBeenCalled();
    expect(click).toHaveBeenCalled();
    click.mockRestore();
  });

  it('refuses a period the API would reject, without calling it', async () => {
    const harness = await open('/reports');
    http
      .expectOne((r) => r.url === '/api/users')
      .flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    http.expectOne((r) => r.url === '/api/reports/summary').flush(REPORT);
    harness.detectChanges();
    await harness.fixture.whenStable();
    const el = harness.routeNativeElement as HTMLElement;
    const [from, to] = Array.from(el.querySelectorAll<HTMLInputElement>('input[type=date]'));
    expect(from.value).toBe('2026-08-05');
    from.value = '2026-12-01';
    from.dispatchEvent(new Event('change'));
    await harness.fixture.whenStable();
    expect(to.value).toBe('2026-11-02');
    expect(el.querySelector('[role=alert]')?.textContent).toContain('précéder');
  });

  it('shows a technician their own figures, without exports nor technician filter', async () => {
    role.set('TECHNICIAN');
    const harness = await open('/reports');
    http.expectOne((r) => r.url === '/api/reports/summary').flush(REPORT);
    harness.detectChanges();
    await harness.fixture.whenStable();
    const el = harness.routeNativeElement as HTMLElement;
    expect(el.querySelector('h1')?.textContent).toContain('Mes statistiques');
    expect(el.querySelector('.exports')).toBeNull();
    expect(el.querySelector('.filters')?.textContent).not.toContain('Technicien');
  });
});
