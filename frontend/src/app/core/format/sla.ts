import { SlaView } from '../models/ticket.models';
import { formatDuration } from './time';

export type SlaTone = 'success' | 'warning' | 'danger' | 'slate';

export interface SlaDisplay {
  tone: SlaTone;
  /** Progress bar fill, 0–100. */
  percent: number;
  label: string;
  paused: boolean;
  /** Tooltip: how the deadline is counted. */
  detail: string;
}

/**
 * What the SLA indicator shows for a ticket, from the state computed by the API. The remaining time is wall-clock
 * time until the deadline (the API already placed the deadline in business hours when the rule requires it).
 * Returns null when the ticket has no SLA.
 */
export function slaDisplay(sla: SlaView, now: number): SlaDisplay | null {
  if (sla.state === 'NONE' || sla.resolutionDueAt === null) {
    return null;
  }
  const percent = Math.max(0, Math.min(100, sla.consumedPercent ?? 0));
  const remaining = Date.parse(sla.resolutionDueAt) - now;
  const detail = sla.businessHoursOnly
    ? 'Délai compté en heures ouvrées'
    : 'Délai compté 24 h / 24, 7 j / 7';
  switch (sla.state) {
    case 'PAUSED':
      return {
        tone: 'slate',
        percent,
        label: 'En pause',
        paused: true,
        detail: 'Chrono suspendu pendant l’attente',
      };
    case 'MET':
      return { tone: 'success', percent: 100, label: 'Respecté', paused: false, detail };
    case 'MISSED':
      return { tone: 'danger', percent: 100, label: 'Non respecté', paused: false, detail };
    case 'BREACHED':
      return {
        tone: 'danger',
        percent: 100,
        label: `Dépassé de ${formatDuration(remaining)}`,
        paused: false,
        detail,
      };
    case 'AT_RISK':
      return {
        tone: 'warning',
        percent,
        label: `Reste ${formatDuration(remaining)}`,
        paused: false,
        detail,
      };
    case 'ON_TRACK':
      return {
        tone: 'success',
        percent,
        label: `Reste ${formatDuration(remaining)}`,
        paused: false,
        detail,
      };
  }
}
