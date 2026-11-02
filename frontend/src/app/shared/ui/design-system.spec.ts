import { Dialog } from '@angular/cdk/dialog';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Ticket } from 'lucide-angular';

import { PriorityCode, TicketStatus } from '../../core/models/ticket.models';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { PriorityBadgeComponent } from './priority-badge.component';
import { StatCardComponent } from './stat-card.component';
import { StatusBadgeComponent } from './status-badge.component';
import { ToastContainerComponent } from './toast-container.component';

@Component({
  imports: [StatusBadgeComponent, PriorityBadgeComponent, StatCardComponent],
  template: `
    <nx-status-badge [status]="status()" />
    <nx-priority-badge [code]="priority()" />
    <nx-stat-card
      label="En retard"
      value="6"
      [icon]="icon"
      [change]="change()"
      goodWhen="down"
      [loading]="loading()"
    />
  `,
})
class HostComponent {
  readonly status = signal<TicketStatus>('IN_PROGRESS');
  readonly priority = signal<PriorityCode>('CRITICAL');
  readonly change = signal<number | null>(20);
  readonly loading = signal(false);
  readonly icon = Ticket;
}

describe('design system components', () => {
  let fixture: ComponentFixture<HostComponent>;
  let el: HTMLElement;

  beforeEach(() => {
    fixture = TestBed.createComponent(HostComponent);
    el = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  it('labels and colours statuses and priorities', () => {
    const status = el.querySelector('nx-status-badge .badge')!;
    expect(status.textContent?.trim()).toBe('En cours');
    expect(status.classList).toContain('badge--violet');

    const priority = el.querySelector('nx-priority-badge .badge')!;
    expect(priority.textContent?.trim()).toBe('Critique');
    expect(priority.classList).toContain('badge--danger');
    expect(priority.querySelector('.pulse')).not.toBeNull();

    fixture.componentInstance.priority.set('LOW');
    fixture.detectChanges();
    expect(el.querySelector('nx-priority-badge .pulse')).toBeNull();
  });

  it('colours a trend by whether it is good news', () => {
    const trend = () => el.querySelector('.stat__trend')!;
    expect(trend().classList).toContain('stat__trend--bad');
    expect(trend().textContent).toContain('+20 %');

    fixture.componentInstance.change.set(-12.5);
    fixture.detectChanges();
    expect(trend().classList).toContain('stat__trend--good');
    expect(trend().textContent).toContain('-12,5 %');

    fixture.componentInstance.change.set(null);
    fixture.detectChanges();
    expect(el.querySelector('.stat__trend')).toBeNull();
  });

  it('shows skeletons instead of the value while loading', () => {
    fixture.componentInstance.loading.set(true);
    fixture.detectChanges();
    expect(el.querySelector('.stat__value')).toBeNull();
    expect(el.querySelectorAll('nx-stat-card .skeleton').length).toBe(2);
  });
});

describe('ToastService', () => {
  it('stacks toasts, dismisses them and removes them after a delay', () => {
    vi.useFakeTimers();
    try {
      const service = TestBed.inject(ToastService);
      const fixture = TestBed.createComponent(ToastContainerComponent);
      service.success('Ticket créé.');
      service.error('Erreur serveur.', 'Référence : abc123');
      fixture.detectChanges();

      const toasts = (fixture.nativeElement as HTMLElement).querySelectorAll('.toast');
      expect(toasts.length).toBe(2);
      expect(toasts[1].getAttribute('role')).toBe('alert');
      expect(toasts[1].textContent).toContain('abc123');

      vi.advanceTimersByTime(4000);
      expect(service.toasts().map((t) => t.message)).toEqual(['Erreur serveur.']);
      vi.advanceTimersByTime(4000);
      expect(service.toasts()).toEqual([]);
    } finally {
      vi.useRealTimers();
    }
  });
});

describe('ConfirmService', () => {
  afterEach(() => TestBed.inject(Dialog).closeAll());

  function dialog(): HTMLElement {
    return document.querySelector('nx-confirm-dialog') as HTMLElement;
  }

  it('requires the reason when asked, and returns it trimmed', () => {
    const results: unknown[] = [];
    TestBed.inject(ConfirmService)
      .confirm({
        title: 'Rejeter',
        message: 'Le demandeur sera notifié.',
        confirmLabel: 'Rejeter',
        tone: 'danger',
        reason: { label: 'Motif', required: true, maxLength: 100 },
      })
      .subscribe((result) => results.push(result));
    TestBed.inject(Dialog).openDialogs[0].componentRef!.changeDetectorRef.detectChanges();

    const submit = dialog().querySelector('button[type=submit]') as HTMLButtonElement;
    expect(submit.disabled).toBe(true);

    const textarea = dialog().querySelector('textarea')!;
    textarea.value = '  Doublon de INC-2026-0012  ';
    textarea.dispatchEvent(new Event('input'));
    TestBed.inject(Dialog).openDialogs[0].componentRef!.changeDetectorRef.detectChanges();
    expect(submit.disabled).toBe(false);

    submit.click();
    expect(results).toEqual([{ reason: 'Doublon de INC-2026-0012' }]);
  });

  it('emits null when cancelled', () => {
    const results: unknown[] = [];
    TestBed.inject(ConfirmService)
      .confirm({ title: 'Archiver', message: 'Confirmez.', confirmLabel: 'Archiver' })
      .subscribe((result) => results.push(result));
    TestBed.inject(Dialog).openDialogs[0].componentRef!.changeDetectorRef.detectChanges();

    Array.from(dialog().querySelectorAll('button'))
      .find((b) => b.textContent?.includes('Annuler'))!
      .click();
    expect(results).toEqual([null]);
  });
});
