import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SlaRule } from './settings.models';
import { SlaCalendarComponent } from './sla-calendar.component';
import { SlaRulesComponent, humanDuration, ruleError } from './sla-rules.component';
import { nowInZone, zonedToInstant } from './zoned-time';

describe('zoned time', () => {
  it('reads a wall-clock time in the company zone, summer and winter', () => {
    expect(zonedToInstant('2026-11-02T18:30', 'Europe/Paris')).toBe('2026-11-02T17:30:00.000Z');
    expect(zonedToInstant('2026-07-01T08:00', 'Europe/Paris')).toBe('2026-07-01T06:00:00.000Z');
    expect(zonedToInstant('2026-11-02T18:30', 'UTC')).toBe('2026-11-02T18:30:00.000Z');
    expect(zonedToInstant('', 'Europe/Paris')).toBeNull();
  });

  it('handles the day of a clock change', () => {
    // 29 March 2026: Paris goes from UTC+1 to UTC+2 at 02:00.
    expect(zonedToInstant('2026-03-29T01:30', 'Europe/Paris')).toBe('2026-03-29T00:30:00.000Z');
    expect(zonedToInstant('2026-03-29T10:00', 'Europe/Paris')).toBe('2026-03-29T08:00:00.000Z');
  });

  it('gives the current time in a zone for the input', () => {
    expect(nowInZone(Date.parse('2026-11-02T17:30:00Z'), 'Europe/Paris')).toBe('2026-11-02T18:30');
  });
});

const RULE: SlaRule = {
  id: 3,
  priority: 'HIGH',
  priorityLabel: 'Haute',
  priorityColor: '#EA580C',
  firstResponseMinutes: 60,
  resolutionMinutes: 480,
  businessHoursOnly: true,
  warningThresholdPercent: 75,
};

describe('SLA rules', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('keeps business-hours targets in hours, calendar targets in days', () => {
    expect(humanDuration(1440, true)).toBe('24 h ouvrées');
    expect(humanDuration(15, true)).toBe('15 min ouvrées');
    expect(humanDuration(1440, false)).toBe('1 j');
    expect(humanDuration(120, false)).toBe('2 h');
  });

  it('checks the same limits as the API', () => {
    expect(ruleError(RULE)).toBeNull();
    expect(ruleError({ ...RULE, resolutionMinutes: 60 })).toContain('plus de temps');
    expect(ruleError({ ...RULE, warningThresholdPercent: 40 })).toContain('50 à 95');
    expect(ruleError({ ...RULE, firstResponseMinutes: 0 })).toContain('Première réponse');
  });

  it('saves only the edited rule, once it is valid', async () => {
    const fixture = TestBed.createComponent(SlaRulesComponent);
    fixture.autoDetectChanges();
    http.expectOne('/api/sla/rules').flush([RULE]);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.row-actions button')).toBeNull();
    expect(el.textContent).toContain('8 h ouvrées');

    const resolution = el.querySelectorAll<HTMLInputElement>('input[type=number]')[1];
    resolution.value = '30';
    resolution.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(el.querySelector('.row-actions .field__error')?.textContent).toContain('plus de temps');
    expect(el.querySelector<HTMLButtonElement>('.row-actions button')!.disabled).toBe(true);

    resolution.value = '600';
    resolution.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    el.querySelector<HTMLButtonElement>('.row-actions button')!.click();
    const put = http.expectOne({ method: 'PUT', url: '/api/sla/rules/3' });
    expect(put.request.body).toEqual({
      firstResponseMinutes: 60,
      resolutionMinutes: 600,
      businessHoursOnly: true,
      warningThresholdPercent: 75,
    });
    put.flush({ ...RULE, resolutionMinutes: 600 });
  });

  it('simulates a deadline for a time typed in the company time zone', async () => {
    const fixture = TestBed.createComponent(SlaCalendarComponent);
    fixture.autoDetectChanges();
    http.expectOne('/api/sla/calendar').flush({
      timeZone: 'Europe/Paris',
      workStart: '08:00:00',
      workEnd: '18:00:00',
      workingDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
    });
    http.expectOne('/api/sla/holidays').flush([{ id: 1, day: '2026-12-25', label: 'Noël' }]);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector<HTMLInputElement>('input[type=time]')!.value).toBe('08:00');
    expect(el.textContent).toContain('Noël');

    const from = el.querySelector<HTMLInputElement>('input[type=datetime-local]')!;
    from.value = '2026-11-06T17:00';
    from.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.includes('Calculer'))!
      .click();

    const preview = http.expectOne((r) => r.url === '/api/sla/preview');
    expect(preview.request.params.get('priority')).toBe('HIGH');
    expect(preview.request.params.get('from')).toBe('2026-11-06T16:00:00.000Z');
    preview.flush({
      priority: 'HIGH',
      from: '2026-11-06T16:00:00Z',
      businessHoursOnly: true,
      firstResponseDueAt: '2026-11-09T07:00:00Z',
      resolutionDueAt: '2026-11-09T14:00:00Z',
    });
    await fixture.whenStable();
    expect(el.querySelector('.facts')?.textContent).toContain('09/11/2026 08:00');
  });
});
