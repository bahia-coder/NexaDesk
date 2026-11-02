import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthResponse } from '../../core/auth/auth.models';
import { LoginPage } from './login.page';

const AUTH: AuthResponse = {
  accessToken: 't',
  tokenType: 'Bearer',
  expiresAt: '2026-11-02T12:15:00Z',
  user: {
    id: 1,
    email: 'admin@nexadesk.test',
    firstName: 'Amina',
    lastName: 'Benali',
    fullName: 'Amina Benali',
    role: 'ADMIN',
    department: null,
    jobTitle: null,
    avatarColor: '#4f46e5',
    mustChangePassword: false,
  },
};

describe('LoginPage', () => {
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  let stable: () => Promise<void>;

  function render(returnUrl?: string): HTMLElement {
    const fixture = TestBed.createComponent(LoginPage);
    if (returnUrl) {
      fixture.componentRef.setInput('returnUrl', returnUrl);
    }
    fixture.autoDetectChanges();
    stable = () => fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function type(el: HTMLElement, selector: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  it('validates the form before calling the API, then shows the server refusal', async () => {
    const el = render();
    http.expectOne('/api/auth/demo-accounts').flush(null, { status: 404, statusText: 'Not Found' });

    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await stable();
    expect(el.textContent).toContain('L’e-mail est obligatoire.');
    http.expectNone('/api/auth/login');

    type(el, 'input[type=email]', 'sara@nexadesk.test');
    type(el, 'input[formcontrolname=password]', 'mauvais');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    http
      .expectOne('/api/auth/login')
      .flush(
        { code: 'INVALID_CREDENTIALS', detail: 'E-mail ou mot de passe incorrect.' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await stable();
    expect(el.querySelector('[role=alert]')?.textContent).toContain(
      'E-mail ou mot de passe incorrect.',
    );
    // No demo accounts outside the demo profile.
    expect(el.textContent).not.toContain('Comptes de démonstration');
  });

  it('signs in with a demo account in one click and returns to the requested page', async () => {
    const el = render('/tickets');
    http.expectOne('/api/auth/demo-accounts').flush([
      {
        role: 'ADMIN',
        label: 'Administrateur',
        email: 'admin@nexadesk.test',
        password: 'Demo@Admin2026',
      },
    ]);
    await stable();

    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.includes('Administrateur'))!
      .click();
    const login = http.expectOne('/api/auth/login');
    expect(login.request.body).toEqual({
      email: 'admin@nexadesk.test',
      password: 'Demo@Admin2026',
    });
    login.flush(AUTH);

    expect(navigate).toHaveBeenCalledWith('/tickets');
  });

  it('sends a user with a temporary password to the password change first', () => {
    const el = render('/tickets');
    http.expectOne('/api/auth/demo-accounts').flush([]);
    type(el, 'input[type=email]', 'new@nexadesk.test');
    type(el, 'input[formcontrolname=password]', 'Temp@2026xx');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    http
      .expectOne('/api/auth/login')
      .flush({ ...AUTH, user: { ...AUTH.user, mustChangePassword: true } });

    expect(navigate).toHaveBeenCalledWith('/change-password');
  });
});
