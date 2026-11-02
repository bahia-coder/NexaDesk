import { Dialog } from '@angular/cdk/dialog';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { AuthService } from '../../core/auth/auth.service';
import { Role } from '../../core/models/user.models';
import { ProfilePage } from './profile.page';
import { UserDetailPage } from './user-detail.page';
import { UserFormDialogComponent } from './user-form-dialog.component';
import { User, accountState } from './users.models';
import { UsersPage } from './users.page';

const NOW = Date.parse('2026-11-02T12:00:00Z');

const USER: User = {
  id: 9,
  email: 'karim@nexadesk.test',
  firstName: 'Karim',
  lastName: 'Belhadj',
  fullName: 'Karim Belhadj',
  role: 'TECHNICIAN',
  departmentId: 1,
  departmentName: 'Informatique',
  jobTitle: 'Technicien réseau',
  phone: null,
  avatarColor: '#0891b2',
  active: true,
  mustChangePassword: false,
  lockedUntil: null,
  lastLoginAt: '2026-11-02T08:00:00Z',
  createdAt: '2026-01-02T08:00:00Z',
  updatedAt: '2026-01-02T08:00:00Z',
};

describe('accountState', () => {
  it('shows the most important state of an account', () => {
    expect(accountState(USER, NOW)).toBe('ACTIVE');
    expect(accountState({ ...USER, mustChangePassword: true }, NOW)).toBe('TEMPORARY_PASSWORD');
    expect(accountState({ ...USER, lockedUntil: '2026-11-02T12:10:00Z' }, NOW)).toBe('LOCKED');
    expect(accountState({ ...USER, lockedUntil: '2026-11-02T11:00:00Z' }, NOW)).toBe('ACTIVE');
    expect(accountState({ ...USER, active: false, mustChangePassword: true }, NOW)).toBe(
      'DISABLED',
    );
  });
});

describe('user administration', () => {
  let http: HttpTestingController;

  function signIn(id: number, role: Role): void {
    TestBed.inject(AuthService).login('a@nexadesk.test', 'secret').subscribe();
    http.expectOne('/api/auth/login').flush({
      accessToken: 't',
      tokenType: 'Bearer',
      expiresAt: '2026-11-02T12:00:00Z',
      user: {
        id,
        email: 'a@nexadesk.test',
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

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [
            { path: 'users', component: UsersPage },
            { path: 'users/:id', component: UserDetailPage },
            { path: 'profile', component: ProfilePage },
          ],
          withComponentInputBinding(),
        ),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => TestBed.inject(Dialog).closeAll());

  it('lists accounts with the URL filters', async () => {
    signIn(1, 'ADMIN');
    const harness = await RouterTestingHarness.create('/users?role=TECHNICIAN&active=false');
    http.expectOne('/api/departments').flush([{ id: 1, name: 'Informatique' }]);
    const list = http.expectOne((r) => r.url === '/api/users');
    expect(list.request.params.get('role')).toBe('TECHNICIAN');
    expect(list.request.params.get('active')).toBe('false');
    list.flush({
      content: [{ ...USER, active: false }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    harness.detectChanges();

    const row = harness.routeNativeElement!.querySelector('tbody tr')!;
    expect(row.textContent).toContain('Karim Belhadj');
    expect(row.textContent).toContain('Désactivé');
  });

  it('shows the e-mail conflict under the e-mail field when creating an account', async () => {
    const ref = TestBed.inject(Dialog).open(UserFormDialogComponent, { data: { departments: [] } });
    ref.componentRef!.changeDetectorRef.detectChanges();
    await Promise.resolve();
    const el = document.querySelector('nx-user-form-dialog') as HTMLElement;
    const set = (name: string, value: string) => {
      const input = el.querySelector<HTMLInputElement>(`[formcontrolname=${name}]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    set('firstName', 'Léa');
    set('lastName', 'Martin');
    set('email', 'karim@nexadesk.test');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

    const post = http.expectOne({ method: 'POST', url: '/api/users' });
    expect(post.request.body).toMatchObject({
      firstName: 'Léa',
      role: 'EMPLOYEE',
      departmentId: null,
    });
    post.flush(
      {
        code: 'EMAIL_ALREADY_USED',
        detail: 'Cette adresse e-mail est déjà utilisée par un autre compte.',
      },
      { status: 409, statusText: 'Conflict' },
    );
    ref.componentRef!.changeDetectorRef.detectChanges();
    expect(
      el.querySelector('label:has([formcontrolname=email]) .field__error')?.textContent,
    ).toContain('déjà utilisée');
  });

  it('does not let admins change their own role or disable their own account', async () => {
    signIn(9, 'ADMIN');
    const harness = await RouterTestingHarness.create('/users/9');
    http.expectOne('/api/users/9').flush({ ...USER, role: 'ADMIN' });
    http.expectOne('/api/departments').flush([]);
    http.expectOne('/api/categories').flush([]);
    harness.detectChanges();
    const el = harness.routeNativeElement!;

    expect(el.textContent).toContain('c’est vous');
    expect(el.querySelector<HTMLSelectElement>('select:not([formcontrolname])')!.disabled).toBe(
      true,
    );
    expect(el.textContent).not.toContain('Désactiver le compte');
    expect(el.textContent).toContain('Réinitialiser le mot de passe');
  });

  it('loads a technician’s specialties and saves the new selection', async () => {
    signIn(1, 'ADMIN');
    const harness = await RouterTestingHarness.create('/users/9');
    http.expectOne('/api/users/9').flush(USER);
    http.expectOne('/api/departments').flush([]);
    http.expectOne('/api/categories').flush([
      { id: 1, name: 'Réseau & VPN', active: true, subcategories: [] },
      { id: 2, name: 'Impression', active: true, subcategories: [] },
    ]);
    harness.detectChanges();
    await harness.fixture.whenStable();
    http
      .expectOne('/api/users/9/specialties')
      .flush([{ id: 1, name: 'Réseau & VPN', icon: 'wifi' }]);
    harness.detectChanges();
    const el = harness.routeNativeElement!;

    const boxes = el.querySelectorAll<HTMLInputElement>('.checks input');
    expect(Array.from(boxes).map((b) => b.checked)).toEqual([true, false]);
    boxes[1].click();
    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.includes('Enregistrer les spécialités'))!
      .click();
    const put = http.expectOne({ method: 'PUT', url: '/api/users/9/specialties' });
    expect(put.request.body).toEqual({ categoryIds: [1, 2] });
  });

  it('saves my profile and updates the header avatar at once', async () => {
    signIn(1, 'EMPLOYEE');
    const harness = await RouterTestingHarness.create('/profile');
    http.expectOne('/api/profile').flush({
      id: 1,
      email: 'a@nexadesk.test',
      firstName: 'Amina',
      lastName: 'Benali',
      fullName: 'Amina Benali',
      role: 'EMPLOYEE',
      department: 'Finance',
      jobTitle: null,
      phone: null,
      avatarColor: '#4f46e5',
      lastLoginAt: null,
    });
    harness.detectChanges();
    await harness.fixture.whenStable();
    const el = harness.routeNativeElement!;

    el.querySelector<HTMLButtonElement>('button[aria-label="Couleur #db2777"]')!.click();
    harness.detectChanges();
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    const put = http.expectOne({ method: 'PUT', url: '/api/profile' });
    expect(put.request.body).toEqual({ jobTitle: null, phone: null, avatarColor: '#db2777' });
    put.flush({ avatarColor: '#db2777', jobTitle: null });

    expect(TestBed.inject(AuthService).user()?.avatarColor).toBe('#db2777');
  });
});
