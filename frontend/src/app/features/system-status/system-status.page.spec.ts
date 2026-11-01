import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { SystemStatusPage } from './system-status.page';

describe('SystemStatusPage', () => {
  let fixture: ComponentFixture<SystemStatusPage>;
  let http: HttpTestingController;
  let el: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SystemStatusPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(SystemStatusPage);
    http = TestBed.inject(HttpTestingController);
    el = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('checks the backend on load and shows it as operational', () => {
    http.expectOne('/actuator/health').flush({ status: 'UP' });
    fixture.detectChanges();
    expect(el.textContent).toContain('Opérationnelle');
  });

  it('shows the backend as unreachable and lets the user re-check', () => {
    http.expectOne('/actuator/health').flush(null, { status: 502, statusText: 'Bad Gateway' });
    fixture.detectChanges();
    expect(el.textContent).toContain('Injoignable');

    const recheck = Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Revérifier'));
    recheck!.click();
    http.expectOne('/actuator/health').flush({ status: 'UP' });
    fixture.detectChanges();
    expect(el.textContent).toContain('Opérationnelle');
  });

  it('shows the API documentation link in dev mode (Swagger is disabled in prod)', () => {
    http.expectOne('/actuator/health').flush({ status: 'UP' });
    fixture.detectChanges();
    expect(el.querySelector('a[href="/swagger-ui.html"]')).not.toBeNull();
  });
});
