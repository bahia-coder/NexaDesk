import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { AuthService } from '../../core/auth/auth.service';
import { TicketSummary } from '../../core/models/ticket.models';
import { BarListComponent } from '../../shared/ui/bar-list.component';
import { AdminDashboardComponent } from './admin-dashboard.component';
import {
  bucketLabel,
  changePercent,
  describeActivity,
  formatHours,
  formatMinutes,
  formatPercent,
} from './dashboard.format';
import { AdminDashboard, EmployeeDashboard } from './dashboard.models';
import { DashboardPage } from './dashboard.page';
import { TimelineChartComponent } from './timeline-chart.component';

describe('dashboard formatting', () => {
  it('formats figures in French and shows "—" when nothing was measured', () => {
    expect(formatHours(5.25)).toBe('5,3 h');
    expect(formatHours(null)).toBe('—');
    expect(formatPercent(87.5)).toBe('87,5 %');
    expect(formatMinutes(45)).toBe('45 min');
    expect(formatMinutes(200)).toBe('3 h 20');
    expect(formatMinutes(120)).toBe('2 h');
  });

  it('compares with the previous period only when there is one', () => {
    expect(changePercent(6, 8)).toBe(-25);
    expect(changePercent(6, null)).toBeNull();
    expect(changePercent(6, 0)).toBeNull();
  });

  it('labels days and months on the timeline axis', () => {
    expect(bucketLabel('2026-11-03', 'DAY')).toBe('3 nov.');
    expect(bucketLabel('2026-11-01', 'MONTH')).toBe('nov. 26');
  });

  it('describes history entries as sentences', () => {
    const base = {
      at: '2026-11-02T10:00:00Z',
      ticketId: 1,
      ticketReference: 'INC-2026-0001',
      actorName: 'Karim Belhadj',
      field: null,
      oldValue: null,
      newValue: null,
      comment: null,
    };
    expect(
      describeActivity({
        ...base,
        action: 'STATUS_CHANGED',
        oldValue: 'NEW',
        newValue: 'RESOLVED',
      }),
    ).toBe('Karim Belhadj a passé le ticket au statut « Résolu »');
    expect(describeActivity({ ...base, action: 'FEEDBACK_ADDED', newValue: '5/5' })).toBe(
      'Karim Belhadj a évalué le ticket (5/5)',
    );
    expect(describeActivity({ ...base, action: 'SLA_BREACHED', actorName: null })).toBe(
      'Le SLA du ticket est dépassé',
    );
  });
});

describe('BarListComponent', () => {
  it('sizes bars against the largest value and writes each value', () => {
    const fixture = TestBed.createComponent(BarListComponent);
    fixture.componentRef.setInput('label', 'Par priorité');
    fixture.componentRef.setInput('hideZero', true);
    fixture.componentRef.setInput('items', [
      { label: 'Haute', value: 10, color: '#ea580c' },
      { label: 'Basse', value: 5 },
      { label: 'Critique', value: 0 },
    ]);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    const fills = el.querySelectorAll<HTMLElement>('.bar__fill');
    expect(fills.length).toBe(2);
    expect(fills[0].style.width).toBe('100%');
    expect(fills[1].style.width).toBe('50%');
    expect(fills[0].style.background).toContain('234, 88, 12');
    expect(el.querySelector('.bar__value')?.textContent).toContain('10');
    expect(el.querySelector('ul')?.getAttribute('aria-label')).toBe('Par priorité');
  });
});

@Component({ selector: 'nx-timeline-chart', template: '' })
class TimelineChartStub {
  readonly points = input<unknown>();
  readonly granularity = input<unknown>();
}

const ADMIN: AdminDashboard = {
  period: { from: '2026-10-27', to: '2026-11-02', days: 7, granularity: 'DAY' },
  kpis: {
    created: { value: 12, changePercent: 20 },
    resolved: { value: 9, changePercent: -10 },
    open: 14,
    inProgress: 6,
    pending: 2,
    overdue: 3,
    atRisk: 1,
    unassigned: 4,
    avgResolutionHours: 6,
    avgResolutionHoursPrevious: 8,
    resolutionRate: 75,
  },
  sla: { firstResponseRate: 92, resolutionRate: 80, resolvedMeasured: 10 },
  timeline: [],
  byPriority: [{ key: 'HIGH', label: 'Haute', color: '#EA580C', count: 5 }],
  byStatus: [],
  byCategory: [{ key: '1', label: 'Impression', color: null, count: 3 }],
  technicians: [
    {
      id: 7,
      fullName: 'Karim Belhadj',
      avatarColor: '#0891b2',
      openAssigned: 3,
      resolved: 8,
      avgResolutionHours: 4.5,
      slaRate: 87.5,
      avgRating: 4.2,
      minutesLogged: 200,
    },
  ],
  satisfaction: { average: 4.25, count: 8, distribution: [0, 1, 0, 3, 4] },
  recentActivity: [],
};

function signIn(role: 'ADMIN' | 'EMPLOYEE'): void {
  const auth = TestBed.inject(AuthService);
  auth.login('x@nexadesk.test', 'secret').subscribe();
  TestBed.inject(HttpTestingController)
    .expectOne('/api/auth/login')
    .flush({
      accessToken: 't',
      tokenType: 'Bearer',
      expiresAt: '2026-11-02T12:00:00Z',
      user: {
        id: 1,
        email: 'x@nexadesk.test',
        firstName: 'Amina',
        lastName: 'Benali',
        fullName: 'Amina Benali',
        role,
        department: null,
        jobTitle: null,
        avatarColor: '#4f46e5',
        mustChangePassword: false,
      },
    });
}

describe('dashboards', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [{ path: 'dashboard', component: DashboardPage }],
          withComponentInputBinding(),
        ),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    TestBed.overrideComponent(AdminDashboardComponent, {
      remove: { imports: [TimelineChartComponent] },
      add: { imports: [TimelineChartStub] },
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows the admin figures of the period chosen in the URL, and reloads on a new period', async () => {
    signIn('ADMIN');
    const harness = await RouterTestingHarness.create('/dashboard?days=7');
    http.expectOne('/api/dashboard/admin?days=7').flush(ADMIN);
    harness.detectChanges();
    const el = harness.routeNativeElement!;

    expect(el.querySelector('h1')?.textContent).toContain('Bonjour Amina');
    const values = Array.from(el.querySelectorAll('.stat__value')).map((v) =>
      v.textContent?.trim(),
    );
    expect(values).toEqual(['12', '9', '3', '6 h']);
    // Created +20 % and average resolution time −25 % are good news; resolved −10 % is not.
    expect(el.querySelectorAll('.stat__trend--good').length).toBe(2);
    expect(el.querySelectorAll('.stat__trend--bad').length).toBe(1);
    expect(el.querySelector('[aria-pressed="true"]')?.textContent?.trim()).toBe('7 jours');
    expect(el.textContent).toContain('Karim Belhadj');
    expect(el.textContent).toContain('3 h 20');

    Array.from(el.querySelectorAll<HTMLButtonElement>('.segmented__item'))
      .find((b) => b.textContent?.includes('90 jours'))!
      .click();
    await harness.fixture.whenStable();
    http
      .expectOne('/api/dashboard/admin?days=90')
      .flush({ ...ADMIN, period: { ...ADMIN.period, days: 90 } });
  });

  it('offers to retry when the dashboard cannot be loaded', async () => {
    signIn('ADMIN');
    const harness = await RouterTestingHarness.create('/dashboard');
    http
      .expectOne('/api/dashboard/admin?days=30')
      .flush(null, { status: 500, statusText: 'Error' });
    harness.detectChanges();
    const retry = Array.from(harness.routeNativeElement!.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Réessayer'),
    )!;

    retry.click();
    await harness.fixture.whenStable();
    http.expectOne('/api/dashboard/admin?days=30').flush(ADMIN);
  });

  it('highlights the resolutions an employee must confirm', async () => {
    signIn('EMPLOYEE');
    const ticket = {
      id: 3,
      reference: 'INC-2026-0003',
      title: 'Imprimante bloquée',
      status: 'RESOLVED',
      priority: { code: 'MEDIUM', label: 'Moyenne', color: '#2563EB', rank: 2 },
    } as TicketSummary;
    const data: EmployeeDashboard = {
      waiting: 1,
      inProgress: 2,
      resolved: 5,
      toConfirm: 1,
      awaitingConfirmation: [ticket],
      recent: [ticket],
    };
    const harness = await RouterTestingHarness.create('/dashboard');
    http.expectOne('/api/dashboard/employee').flush(data);
    harness.detectChanges();
    const el = harness.routeNativeElement!;

    expect(el.querySelector('.confirm')?.textContent).toContain('INC-2026-0003');
    expect(el.querySelectorAll('.stat__value')[2].textContent?.trim()).toBe('1');
  });
});
