import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { LayoutService } from '../../core/services/layout.service';
import { AppShellComponent } from './app-shell.component';

@Component({ template: '<h1>Liste des tickets</h1>' })
class TicketsStubPage {}

describe('AppShellComponent', () => {
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [
            {
              path: 'app',
              component: AppShellComponent,
              children: [
                { path: 'tickets', component: TicketsStubPage, title: 'NexaDesk · Tickets' },
              ],
            },
          ],
          withComponentInputBinding(),
        ),
      ],
    });
    harness = await RouterTestingHarness.create();
  });

  it("shows the role's navigation, links only to existing pages and marks the current one", async () => {
    await harness.navigateByUrl('/app/tickets?role=TECHNICIAN');
    const root = harness.fixture.nativeElement as HTMLElement;

    const links = Array.from(root.querySelectorAll<HTMLAnchorElement>('a.sidebar__link'));
    expect(links.map((a) => a.textContent?.trim())).toEqual(['Tickets']);
    expect(links[0].getAttribute('aria-current')).toBe('page');
    // Pages not built yet are listed as disabled entries in development, never as links.
    const planned = Array.from(root.querySelectorAll('.sidebar__link--planned')).map((e) =>
      e.textContent?.trim(),
    );
    expect(planned).toContain('Mes interventions');
    expect(planned).not.toContain('Utilisateurs');

    expect(root.querySelector('.header__title')?.textContent).toBe('Tickets');
    expect(root.querySelector('main#main h1')?.textContent).toBe('Liste des tickets');
  });

  it('collapses the sidebar on desktop and remembers it', async () => {
    localStorage.removeItem('nexadesk.sidebar-collapsed');
    await harness.navigateByUrl('/app/tickets?role=ADMIN');
    const root = harness.fixture.nativeElement as HTMLElement;

    (root.querySelector('.sidebar__toggle') as HTMLButtonElement).click();
    harness.detectChanges();

    expect(root.querySelector('.shell')?.classList).toContain('shell--compact');
    expect(localStorage.getItem('nexadesk.sidebar-collapsed')).toBe('true');
    expect(TestBed.inject(LayoutService).compact()).toBe(true);
    TestBed.inject(LayoutService).toggleSidebar();
  });
});
