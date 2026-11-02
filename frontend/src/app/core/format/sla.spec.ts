import { SlaView } from '../models/ticket.models';
import { slaDisplay } from './sla';

const now = Date.parse('2026-11-02T12:00:00Z');
const HOUR = 3_600_000;

function view(
  state: SlaView['state'],
  dueInHours: number | null,
  consumed: number | null,
): SlaView {
  return {
    state,
    firstResponseDueAt: null,
    firstRespondedAt: null,
    firstResponseBreached: false,
    resolutionDueAt: dueInHours === null ? null : new Date(now + dueInHours * HOUR).toISOString(),
    consumedPercent: consumed,
    businessHoursOnly: true,
  };
}

describe('slaDisplay', () => {
  it('shows nothing without an SLA', () => {
    expect(slaDisplay(view('NONE', null, null), now)).toBeNull();
  });

  it('counts down while running, warning once at risk', () => {
    expect(slaDisplay(view('ON_TRACK', 26, 30), now)).toMatchObject({
      tone: 'success',
      percent: 30,
      label: 'Reste 1 j 2 h',
    });
    expect(slaDisplay(view('AT_RISK', 1.5, 82), now)).toMatchObject({
      tone: 'warning',
      label: 'Reste 1 h 30',
    });
  });

  it('reports overdue time and a full red bar once breached', () => {
    expect(slaDisplay(view('BREACHED', -0.5, 130), now)).toMatchObject({
      tone: 'danger',
      percent: 100,
      label: 'Dépassé de 30 min',
    });
  });

  it('is grey and paused while waiting, and final once resolved', () => {
    expect(slaDisplay(view('PAUSED', 4, 40), now)).toMatchObject({
      tone: 'slate',
      paused: true,
      label: 'En pause',
    });
    expect(slaDisplay(view('MET', -10, 70), now)).toMatchObject({
      tone: 'success',
      label: 'Respecté',
    });
    expect(slaDisplay(view('MISSED', -10, 120), now)).toMatchObject({
      tone: 'danger',
      label: 'Non respecté',
    });
  });

  it('explains how the deadline is counted', () => {
    expect(slaDisplay(view('ON_TRACK', 2, 10), now)?.detail).toContain('heures ouvrées');
    expect(
      slaDisplay({ ...view('ON_TRACK', 2, 10), businessHoursOnly: false }, now)?.detail,
    ).toContain('24 h / 24');
  });
});
