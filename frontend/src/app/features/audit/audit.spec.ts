import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { AuditEntry, auditChanges, auditLink } from './audit.models';
import { AuditPage } from './audit.page';

const ROLE_CHANGE: AuditEntry = {
  id: 41,
  actor: { id: 1, fullName: 'Amina Berrada', avatarColor: '#4f46e5' },
  actorEmail: 'admin@nexadesk.test',
  action: 'USER_ROLE_CHANGED',
  entityType: 'USER',
  entityId: '9',
  summary: 'Rôle de employe@nexadesk.test modifié',
  oldValue: { role: 'EMPLOYEE' },
  newValue: { role: 'TECHNICIAN' },
  ipAddress: '127.0.0.1',
  traceId: 'a1b2c3',
  createdAt: '2026-11-03T09:15:00Z',
};

const FAILED_LOGIN: AuditEntry = {
  ...ROLE_CHANGE,
  id: 40,
  actor: null,
  actorEmail: 'inconnu@nexadesk.test',
  action: 'LOGIN_FAILURE',
  entityId: null,
  summary: 'Échec de connexion : compte inconnu',
  oldValue: null,
  newValue: null,
  traceId: null,
};

function page(content: AuditEntry[]) {
  return { content, page: 0, size: 50, totalElements: content.length, totalPages: 1 };
}

describe('Audit rules', () => {
  it('lists only the fields that changed, readably', () => {
    expect(
      auditChanges(
        { name: 'Portable', location: null, tags: ['a', 'b'], brand: 'Dell' },
        { name: 'Portable HP', location: '2e étage', tags: ['a', 'b'], brand: 'Dell' },
      ),
    ).toEqual([
      { field: 'name', before: 'Portable', after: 'Portable HP' },
      { field: 'location', before: '—', after: '2e étage' },
    ]);
    expect(auditChanges(null, { holder: 'sara@nexadesk.test' })).toEqual([
      { field: 'holder', before: '—', after: 'sara@nexadesk.test' },
    ]);
    expect(auditChanges(null, null)).toEqual([]);
  });

  it('opens the audited object where the application shows it', () => {
    expect(auditLink({ entityType: 'USER', entityId: '9' })).toEqual({ path: ['/users', '9'] });
    expect(auditLink({ entityType: 'TICKET', entityId: 'INC-2026-0012' })).toEqual({
      path: ['/tickets'],
      query: { search: 'INC-2026-0012' },
    });
    expect(auditLink({ entityType: 'USER', entityId: null })).toBeNull();
    expect(auditLink({ entityType: 'SOMETHING_NEW', entityId: '3' })).toBeNull();
  });
});

describe('Audit page', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'audit', component: AuditPage }]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('applies the URL filters, shows the before / after values and filters on a person', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/audit?action=USER_ROLE_CHANGED&from=2026-11-01');
    const first = http.expectOne((r) => r.url === '/api/audit');
    expect(first.request.params.get('action')).toBe('USER_ROLE_CHANGED');
    expect(first.request.params.get('from')).toBe('2026-11-01');
    expect(first.request.params.get('size')).toBe('50');
    first.flush(page([ROLE_CHANGE, FAILED_LOGIN]));
    harness.detectChanges();
    await harness.fixture.whenStable();
    const el = harness.routeNativeElement as HTMLElement;

    const rows = el.querySelectorAll('.audit__row');
    expect(rows[0].textContent).toContain('Rôle modifié');
    expect(rows[1].textContent).toContain('inconnu@nexadesk.test');
    expect(rows[1].querySelector('.badge--danger')?.textContent).toContain('Échec de connexion');
    expect(rows[0].querySelector('a')?.getAttribute('href')).toBe('/users/9');

    rows[0].querySelector<HTMLButtonElement>('[aria-label="Voir le détail"]')!.click();
    await harness.fixture.whenStable();
    const diff = el.querySelector('.audit__details')!;
    expect(diff.querySelector('.diff__before')?.textContent).toBe('EMPLOYEE');
    expect(diff.querySelector('.diff__after')?.textContent).toBe('TECHNICIAN');
    expect(diff.textContent).toContain('127.0.0.1');

    rows[0].querySelector<HTMLButtonElement>('.who--button')!.click();
    await harness.fixture.whenStable();
    const byActor = http.expectOne((r) => r.url === '/api/audit');
    expect(byActor.request.params.get('actorId')).toBe('1');
    expect(byActor.request.params.get('action')).toBe('USER_ROLE_CHANGED');
    byActor.flush(page([ROLE_CHANGE]));
    await harness.fixture.whenStable();
    expect(el.querySelector('.chip')?.textContent).toContain('Par Amina Berrada');
  });

  it('refuses an inverted period without calling the API', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/audit');
    http.expectOne((r) => r.url === '/api/audit').flush(page([]));
    harness.detectChanges();
    await harness.fixture.whenStable();
    const el = harness.routeNativeElement as HTMLElement;
    expect(el.textContent).toContain('Journal vide');

    const [from, to] = Array.from(el.querySelectorAll<HTMLInputElement>('input[type=date]'));
    to.value = '2026-11-01';
    from.value = '2026-11-03';
    from.dispatchEvent(new Event('change'));
    await harness.fixture.whenStable();
    expect(el.querySelector('[role=alert]')?.textContent).toContain('précéder');
  });
});
