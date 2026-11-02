import { Dialog } from '@angular/cdk/dialog';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { TicketDetail } from '../tickets/tickets.models';
import { Intervention } from './interventions.models';
import { MyInterventionsPage } from './my-interventions.page';
import { TicketInterventionsComponent } from './ticket-interventions.component';

const INTERVENTION: Intervention = {
  id: 5,
  ticketId: 7,
  ticketReference: 'INC-2026-0007',
  ticketTitle: 'VPN inaccessible',
  technician: { id: 3, fullName: 'Karim Belhadj', avatarColor: '#0891b2' },
  diagnosis: 'Certificat de la passerelle expiré.',
  solution: null,
  timeSpentMinutes: 45,
  startedAt: null,
  endedAt: null,
  createdAt: '2026-11-02T09:00:00Z',
  updatedAt: '2026-11-02T09:00:00Z',
  editable: true,
};

function ticket(actions: TicketDetail['availableActions']): TicketDetail {
  return { id: 7, reference: 'INC-2026-0007', availableActions: actions } as TicketDetail;
}

describe('interventions', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [{ path: 'interventions', component: MyInterventionsPage }],
          withComponentInputBinding(),
        ),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    TestBed.inject(Dialog).closeAll();
    http.verify();
  });

  it('lists the work log and offers to log work only while the ticket allows it', () => {
    const fixture = TestBed.createComponent(TicketInterventionsComponent);
    fixture.componentRef.setInput('ticket', ticket(['COMMENT']));
    fixture.detectChanges();
    http
      .expectOne('/api/tickets/7/interventions')
      .flush({ interventions: [INTERVENTION], totalMinutes: 45 });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.textContent).toContain('1 intervention · 45 min au total');
    expect(el.textContent).toContain('Certificat de la passerelle expiré.');
    expect(el.querySelector('button[aria-label="Modifier l’intervention"]')).not.toBeNull();
    expect(el.textContent).not.toContain('Ajouter une intervention');

    fixture.componentRef.setInput('ticket', ticket(['LOG_INTERVENTION']));
    fixture.detectChanges();
    http
      .expectOne('/api/tickets/7/interventions')
      .flush({ interventions: [INTERVENTION], totalMinutes: 45 });
    fixture.detectChanges();
    expect(el.textContent).toContain('Ajouter une intervention');
  });

  it('validates the dialog, sends the intervention and reports the change', async () => {
    const fixture = TestBed.createComponent(TicketInterventionsComponent);
    fixture.componentRef.setInput('ticket', ticket(['LOG_INTERVENTION']));
    fixture.detectChanges();
    http.expectOne('/api/tickets/7/interventions').flush({ interventions: [], totalMinutes: 0 });
    fixture.detectChanges();
    const changed = vi.fn();
    fixture.componentInstance.changed.subscribe(changed);

    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'))
      .find((b) => b.textContent?.includes('Ajouter'))!
      .click();
    const dialogRef = TestBed.inject(Dialog).openDialogs[0];
    dialogRef.componentRef!.changeDetectorRef.detectChanges();
    const dialog = document.querySelector('nx-intervention-dialog') as HTMLElement;

    dialog.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    dialogRef.componentRef!.changeDetectorRef.detectChanges();
    expect(dialog.textContent).toContain('Le diagnostic est obligatoire.');
    http.expectNone('/api/tickets/7/interventions');

    const diagnosis = dialog.querySelector<HTMLTextAreaElement>(
      'textarea[formcontrolname=diagnosis]',
    )!;
    diagnosis.value = '  Câble réseau défectueux remplacé.  ';
    diagnosis.dispatchEvent(new Event('input'));
    Array.from(dialog.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === '1 h')!
      .click();
    dialog.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

    const post = http.expectOne({ method: 'POST', url: '/api/tickets/7/interventions' });
    expect(post.request.body).toEqual({
      diagnosis: 'Câble réseau défectueux remplacé.',
      solution: null,
      timeSpentMinutes: 60,
    });
    post.flush({ ...INTERVENTION, id: 6 });
    await fixture.whenStable();
    expect(changed).toHaveBeenCalled();
    http.expectOne('/api/tickets/7/interventions').flush({ interventions: [], totalMinutes: 0 });
  });

  it('loads my interventions for the period in the URL', async () => {
    const harness = await RouterTestingHarness.create(
      '/interventions?from=2026-10-01&to=2026-10-31',
    );
    const request = http.expectOne((r) => r.url === '/api/interventions/mine');
    expect(request.request.params.get('from')).toBe('2026-10-01');
    expect(request.request.params.get('to')).toBe('2026-10-31');
    request.flush({
      page: { content: [INTERVENTION], page: 0, size: 20, totalElements: 1, totalPages: 1 },
      totalMinutes: 45,
    });
    harness.detectChanges();
    const el = harness.routeNativeElement!;

    expect(el.querySelector('.stat__value')?.textContent?.trim()).toBe('45 min');
    expect(el.querySelector('tbody tr')?.textContent).toContain('INC-2026-0007');
  });
});
