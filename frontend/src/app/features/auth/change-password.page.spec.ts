import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { ChangePasswordPage } from './change-password.page';

describe('ChangePasswordPage', () => {
  let http: HttpTestingController;
  let el: HTMLElement;
  let stable: () => Promise<void>;

  function type(selector: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
  }

  function submit(): void {
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ChangePasswordPage);
    fixture.autoDetectChanges();
    el = fixture.nativeElement as HTMLElement;
    stable = () => fixture.whenStable();
  });

  afterEach(() => http.verify());

  it('checks the rules and the confirmation live, without calling the API', async () => {
    type('input[formcontrolname=currentPassword]', 'Temp@2026xx');
    type('input[formcontrolname=newPassword]', 'Nouveau-2026');
    type('input[formcontrolname=confirmation]', 'Nouveau-2027');
    submit();
    await stable();

    expect(el.textContent).toContain('Les deux mots de passe ne correspondent pas.');
    expect(el.querySelectorAll('.rules__ok').length).toBe(5);
    http.expectNone('/api/profile/password');

    type('input[formcontrolname=newPassword]', 'court');
    await stable();
    expect(el.querySelectorAll('.rules__ok').length).toBe(1);
  });

  it('shows the server refusal of a wrong current password', async () => {
    type('input[formcontrolname=currentPassword]', 'faux');
    type('input[formcontrolname=newPassword]', 'Nouveau-2026');
    type('input[formcontrolname=confirmation]', 'Nouveau-2026');
    submit();

    const request = http.expectOne('/api/profile/password');
    expect(request.request.body).toEqual({ currentPassword: 'faux', newPassword: 'Nouveau-2026' });
    request.flush(
      { code: 'INVALID_CURRENT_PASSWORD', detail: 'Le mot de passe actuel est incorrect.' },
      { status: 422, statusText: 'Unprocessable Entity' },
    );
    await stable();

    expect(el.querySelector('[role=alert]')?.textContent).toContain(
      'Le mot de passe actuel est incorrect.',
    );
  });
});
