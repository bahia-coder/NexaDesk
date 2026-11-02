import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';

import { authInterceptor, errorInterceptor } from '../http/interceptors';
import { ToastService } from '../services/toast.service';
import { AuthResponse, CurrentUser } from './auth.models';
import { authGuard, passwordChangedGuard, safeReturnUrl } from './auth.guards';
import { AuthService } from './auth.service';
import { passwordRules } from './password-rules';

const USER: CurrentUser = {
  id: 1,
  email: 'sara@nexadesk.test',
  firstName: 'Sara',
  lastName: 'Alaoui',
  fullName: 'Sara Alaoui',
  role: 'EMPLOYEE',
  department: 'Finance',
  jobTitle: null,
  avatarColor: '#db2777',
  mustChangePassword: false,
};

function response(token: string, user: Partial<CurrentUser> = {}): AuthResponse {
  return {
    accessToken: token,
    tokenType: 'Bearer',
    expiresAt: '2026-11-02T12:15:00Z',
    user: { ...USER, ...user },
  };
}

describe('authentication', () => {
  let http: HttpTestingController;
  let auth: AuthService;
  let client: HttpClient;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    client = TestBed.inject(HttpClient);
  });

  afterEach(() => http.verify());

  function signIn(user: Partial<CurrentUser> = {}): void {
    auth.login('sara@nexadesk.test', 'secret').subscribe();
    http.expectOne('/api/auth/login').flush(response('token-1', user));
  }

  it('keeps the session in memory after signing in and sends the token to the API only', () => {
    signIn();
    expect(auth.user()?.fullName).toBe('Sara Alaoui');
    expect(auth.role()).toBe('EMPLOYEE');

    client.get('/api/tickets').subscribe();
    expect(http.expectOne('/api/tickets').request.headers.get('Authorization')).toBe(
      'Bearer token-1',
    );
    client.get('/assets/logo.svg').subscribe();
    expect(http.expectOne('/assets/logo.svg').request.headers.has('Authorization')).toBe(false);
  });

  it('renews an expired token once for concurrent requests, then replays them', () => {
    signIn();
    const results: string[] = [];
    client.get<string>('/api/a').subscribe((r) => results.push(r));
    client.get<string>('/api/b').subscribe((r) => results.push(r));

    http.expectOne('/api/a').flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/b').flush(null, { status: 401, statusText: 'Unauthorized' });
    // A single refresh for both: the server rotates the refresh token, a second call would be a reuse.
    http.expectOne('/api/auth/refresh').flush(response('token-2'));

    const replayA = http.expectOne('/api/a');
    const replayB = http.expectOne('/api/b');
    expect(replayA.request.headers.get('Authorization')).toBe('Bearer token-2');
    replayA.flush('A');
    replayB.flush('B');
    expect(results).toEqual(['A', 'B']);
  });

  it('sends the user back to the sign-in page when the session cannot be renewed', () => {
    signIn();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    client.get('/api/tickets').subscribe({ error: () => undefined });
    http.expectOne('/api/tickets').flush(null, { status: 401, statusText: 'Unauthorized' });
    http
      .expectOne('/api/auth/refresh')
      .flush(
        { code: 'INVALID_REFRESH_TOKEN', detail: 'Votre session a expiré.' },
        { status: 401, statusText: 'Unauthorized' },
      );

    expect(auth.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(
      ['/login'],
      expect.objectContaining({
        queryParams: expect.objectContaining({ expired: 1 }),
      }),
    );
  });

  it('restores a session from the refresh cookie at startup, and never fails without one', async () => {
    const failed = auth.restoreSession();
    http.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });
    await failed;
    expect(auth.isAuthenticated()).toBe(false);

    const restored = auth.restoreSession();
    http.expectOne('/api/auth/refresh').flush(response('token-3'));
    await restored;
    expect(auth.token()).toBe('token-3');
  });

  it('shows server errors with their trace id, but leaves validation errors to forms', () => {
    signIn();
    const toasts = TestBed.inject(ToastService);

    client.get('/api/tickets').subscribe({ error: () => undefined });
    http
      .expectOne('/api/tickets')
      .flush(
        {
          code: 'INTERNAL_ERROR',
          detail: 'Une erreur inattendue est survenue.',
          traceId: 'ab12cd34',
        },
        { status: 500, statusText: 'Server Error' },
      );
    client.post('/api/tickets', {}).subscribe({ error: () => undefined });
    http
      .expectOne({ method: 'POST', url: '/api/tickets' })
      .flush(
        { code: 'VALIDATION_FAILED', detail: 'Certaines données sont invalides.', errors: [] },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(toasts.toasts().map((t) => [t.message, t.detail])).toEqual([
      ['Une erreur inattendue est survenue.', 'Référence : ab12cd34'],
    ]);
  });

  it('guards: sign-in required, then the temporary password must be changed first', () => {
    const run = (guard: () => unknown) => TestBed.runInInjectionContext(guard) as boolean | UrlTree;

    const redirect = run(() => authGuard({}, [{ path: 'tickets' } as never])) as UrlTree;
    expect(redirect.toString()).toBe('/login?returnUrl=%2Ftickets');

    signIn({ mustChangePassword: true });
    expect(run(() => authGuard({}, []))).toBe(true);
    expect((run(() => passwordChangedGuard({} as never, {} as never)) as UrlTree).toString()).toBe(
      '/change-password',
    );
  });

  it('only returns to internal pages after signing in', () => {
    expect(safeReturnUrl('/tickets?status=NEW')).toBe('/tickets?status=NEW');
    expect(safeReturnUrl('//evil.example')).toBe('/');
    expect(safeReturnUrl('https://evil.example')).toBe('/');
    expect(safeReturnUrl(undefined)).toBe('/');
  });
});

describe('passwordRules', () => {
  it('mirrors the server rule', () => {
    expect(passwordRules('Demo@Tech2026').every((rule) => rule.ok)).toBe(true);
    const weak = passwordRules('motdepasse');
    expect(weak.filter((rule) => !rule.ok).map((rule) => rule.label)).toEqual([
      'Une majuscule',
      'Un chiffre',
      'Un caractère spécial',
    ]);
    expect(passwordRules('Él€ve-2026x').every((rule) => rule.ok)).toBe(true);
  });
});
