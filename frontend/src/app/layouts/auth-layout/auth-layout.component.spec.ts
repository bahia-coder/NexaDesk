import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { AuthLayoutComponent } from './auth-layout.component';

@Component({ template: '<form aria-label="Connexion"></form>' })
class FormStubPage {}

describe('AuthLayoutComponent', () => {
  it('renders the public page next to the product panel', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: '',
            component: AuthLayoutComponent,
            children: [{ path: 'login', component: FormStubPage }],
          },
        ]),
      ],
    });
    const harness = await RouterTestingHarness.create('/login');
    const root = harness.fixture.nativeElement as HTMLElement;

    expect(root.querySelector('main form[aria-label="Connexion"]')).not.toBeNull();
    expect(root.querySelector('.auth__visual')?.getAttribute('aria-hidden')).toBe('true');
  });
});
