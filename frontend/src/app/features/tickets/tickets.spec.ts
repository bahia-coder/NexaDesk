import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { AuthService } from '../../core/auth/auth.service';
import { TicketSummary } from '../../core/models/ticket.models';
import { Role } from '../../core/models/user.models';
import { moveAction } from './board-moves';
import { TicketDetailPage } from './detail/ticket-detail.page';
import { activeFilterCount, nextSort, paramsFromQuery, queryFromParams } from './ticket-query';
import { TicketsPage } from './tickets.page';
import { priorityFor } from './tickets.labels';
import { TicketDetail } from './tickets.models';

describe('ticket list URL state', () => {
  it('reads filters, sort, page and view from the URL, ignoring invalid values', () => {
    const query = queryFromParams({
      search: '  vpn ',
      status: ['NEW', 'HACKED'],
      priority: 'CRITICAL',
      categoryId: '3',
      scope: 'ASSIGNED_TO_ME',
      sla: 'nope',
      sort: 'password,asc',
      page: '2',
      size: '1000',
      view: 'board',
    });
    expect(query.filter).toEqual({
      search: 'vpn',
      status: ['NEW'],
      priority: ['CRITICAL'],
      type: undefined,
      categoryId: 3,
      scope: 'ASSIGNED_TO_ME',
      sla: undefined,
    });
    expect(query.sort).toBe('createdAt,desc');
    expect(query.page).toBe(2);
    expect(query.size).toBe(20);
    expect(query.view).toBe('board');
  });

  it('writes short URLs (defaults left out) and round-trips', () => {
    const query = queryFromParams({ status: 'PENDING', sort: 'resolutionDueAt,asc' });
    const params = paramsFromQuery(query);
    expect(params).toMatchObject({
      status: ['PENDING'],
      sort: 'resolutionDueAt,asc',
      page: null,
      view: null,
    });
    expect(queryFromParams({ status: 'PENDING', sort: 'resolutionDueAt,asc' })).toEqual(query);
    expect(activeFilterCount(query.filter)).toBe(1);
  });

  it('sorts a column ascending, then descending', () => {
    expect(nextSort('createdAt,desc', 'reference')).toBe('reference,asc');
    expect(nextSort('reference,asc', 'reference')).toBe('reference,desc');
  });
});

describe('workflow helpers', () => {
  it('maps a Kanban move to the workflow action, by role', () => {
    expect(moveAction('NEW', 'ASSIGNED', 'TECHNICIAN')).toBe('TAKE');
    expect(moveAction('NEW', 'ASSIGNED', 'ADMIN')).toBe('ASSIGN');
    expect(moveAction('IN_PROGRESS', 'PENDING', 'TECHNICIAN')).toBe('PUT_ON_HOLD');
    expect(moveAction('IN_PROGRESS', 'RESOLVED', 'ADMIN')).toBe('RESOLVE');
    expect(moveAction('NEW', 'RESOLVED', 'ADMIN')).toBeNull();
    expect(moveAction('RESOLVED', 'IN_PROGRESS', 'ADMIN')).toBeNull();
  });

  it('computes the priority with the same matrix as the server', () => {
    expect(priorityFor('LOW', 'LOW')).toBe('LOW');
    expect(priorityFor('LOW', 'HIGH')).toBe('MEDIUM');
    expect(priorityFor('HIGH', 'MEDIUM')).toBe('HIGH');
    expect(priorityFor('HIGH', 'HIGH')).toBe('CRITICAL');
  });
});

const SUMMARY = {
  id: 7,
  reference: 'INC-2026-0007',
  type: 'INCIDENT',
  title: 'VPN inaccessible',
  status: 'IN_PROGRESS',
  priority: { code: 'HIGH', label: 'Haute', color: '#EA580C', rank: 3 },
  category: { id: 1, name: 'Réseau & VPN', icon: 'wifi' },
  requester: { id: 2, fullName: 'Sara Alaoui', avatarColor: '#db2777' },
  assignee: { id: 3, fullName: 'Karim Belhadj', avatarColor: '#0891b2' },
  sla: {
    state: 'ON_TRACK',
    firstResponseDueAt: null,
    firstRespondedAt: null,
    firstResponseBreached: false,
    resolutionDueAt: '2026-11-03T10:00:00Z',
    consumedPercent: 20,
    businessHoursOnly: true,
  },
  createdAt: '2026-11-02T08:00:00Z',
  updatedAt: '2026-11-02T09:00:00Z',
} as TicketSummary;

const DETAIL: TicketDetail = {
  ...SUMMARY,
  description: 'Erreur 809 depuis ce matin.',
  impact: 'MEDIUM',
  urgency: 'HIGH',
  subcategory: null,
  equipment: null,
  pendingReason: null,
  rejectionReason: null,
  resolution: null,
  resolvedAt: null,
  closedAt: null,
  reopenCount: 0,
  version: 4,
  feedback: null,
  availableActions: [
    'PUT_ON_HOLD',
    'RESOLVE',
    'COMMENT',
    'COMMENT_INTERNAL',
    'ADD_ATTACHMENT',
    'EDIT',
  ],
};

describe('ticket screens', () => {
  let http: HttpTestingController;

  function signIn(role: Role): void {
    TestBed.inject(AuthService).login('x@nexadesk.test', 'secret').subscribe();
    http.expectOne('/api/auth/login').flush({
      accessToken: 't',
      tokenType: 'Bearer',
      expiresAt: '2026-11-02T12:00:00Z',
      user: {
        id: 3,
        email: 'x@nexadesk.test',
        firstName: 'Karim',
        lastName: 'Belhadj',
        fullName: 'Karim Belhadj',
        role,
        department: null,
        jobTitle: null,
        avatarColor: '#0891b2',
        mustChangePassword: false,
      },
    });
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [
            { path: 'tickets', component: TicketsPage },
            { path: 'tickets/:id', component: TicketDetailPage },
          ],
          withComponentInputBinding(),
        ),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('lists tickets with the URL filters, and changing a filter goes back to page 1', async () => {
    signIn('TECHNICIAN');
    const harness = await RouterTestingHarness.create('/tickets?status=IN_PROGRESS&page=1');
    http.expectOne('/api/categories').flush([]);
    const list = http.expectOne((r) => r.url === '/api/tickets');
    expect(list.request.params.getAll('status')).toEqual(['IN_PROGRESS']);
    expect(list.request.params.get('page')).toBe('1');
    expect(list.request.params.get('sort')).toBe('createdAt,desc');
    list.flush({ content: [SUMMARY], page: 1, size: 20, totalElements: 21, totalPages: 2 });
    harness.detectChanges();
    const el = harness.routeNativeElement!;

    expect(el.querySelector('tbody tr')?.textContent).toContain('INC-2026-0007');
    expect(el.textContent).toContain('Réinitialiser (1)');

    const priority = el.querySelector<HTMLSelectElement>('select[aria-label="Priorité"]')!;
    priority.value = 'CRITICAL';
    priority.dispatchEvent(new Event('change'));
    await harness.fixture.whenStable();

    const router = TestBed.inject(Router);
    expect(router.url).toContain('priority=CRITICAL');
    expect(router.url).not.toContain('page=');
    http
      .expectOne((r) => r.url === '/api/tickets')
      .flush({
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      });
  });

  it('shows only the actions the API allows, and runs one', async () => {
    signIn('TECHNICIAN');
    const harness = await RouterTestingHarness.create('/tickets/7');
    http.expectOne('/api/tickets/7').flush(DETAIL);
    http.expectOne('/api/tickets/7/comments').flush([]);
    http.expectOne('/api/tickets/7/attachments').flush([]);
    http.expectOne('/api/tickets/7/history').flush([]);
    harness.detectChanges();
    const el = harness.routeNativeElement!;

    const actions = Array.from(el.querySelectorAll('.actions button')).map((b) =>
      b.textContent?.trim(),
    );
    expect(actions).toEqual(['Résoudre', 'Mettre en attente', 'Modifier']);
    expect(el.querySelector('.segmented')?.textContent).toContain('Interne');

    // Internal note: posted with internal=true, then the ticket is refreshed.
    await harness.fixture.whenStable(); // ngModel registers its control asynchronously.
    const textarea = el.querySelector<HTMLTextAreaElement>('#reply')!;
    textarea.value = 'Certificat expiré côté passerelle.';
    textarea.dispatchEvent(new Event('input'));
    Array.from(el.querySelectorAll<HTMLButtonElement>('.segmented__item'))
      .find((b) => b.textContent?.includes('Interne'))!
      .click();
    await harness.fixture.whenStable();
    el.querySelector<HTMLButtonElement>('.composer button[type=submit]')!.click();

    const post = http.expectOne({ method: 'POST', url: '/api/tickets/7/comments' });
    expect(post.request.body).toEqual({
      body: 'Certificat expiré côté passerelle.',
      internal: true,
    });
    post.flush({
      id: 1,
      author: SUMMARY.assignee,
      body: 'x',
      internal: true,
      createdAt: SUMMARY.createdAt,
    });
    http.expectOne('/api/tickets/7').flush(DETAIL);
    // Comments, attachments and history reload on the next change detection (signal-driven).
    await harness.fixture.whenStable();
    http.expectOne('/api/tickets/7/comments').flush([]);
    http.expectOne('/api/tickets/7/attachments').flush([]);
    http.expectOne('/api/tickets/7/history').flush([]);
  });

  it('reports a ticket that does not exist or is not visible', async () => {
    signIn('EMPLOYEE');
    const harness = await RouterTestingHarness.create('/tickets/99');
    http
      .expectOne('/api/tickets/99')
      .flush(
        { code: 'RESOURCE_NOT_FOUND', detail: 'Ticket introuvable : 99' },
        { status: 404, statusText: 'Not Found' },
      );
    http
      .expectOne('/api/tickets/99/comments')
      .flush(null, { status: 404, statusText: 'Not Found' });
    http
      .expectOne('/api/tickets/99/attachments')
      .flush(null, { status: 404, statusText: 'Not Found' });
    http.expectOne('/api/tickets/99/history').flush(null, { status: 404, statusText: 'Not Found' });
    harness.detectChanges();

    expect(harness.routeNativeElement!.textContent).toContain('Ticket introuvable');
  });
});
