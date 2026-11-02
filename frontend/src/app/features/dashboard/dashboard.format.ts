import { STATUS_LABELS } from '../../core/format/labels';
import { TicketStatus } from '../../core/models/ticket.models';
import { ActivityItem } from './dashboard.models';

// Pure formatting of the dashboard figures (French), unit-tested. No figure is computed here: the API sends them.

const DECIMAL = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 });
const DAY = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short', timeZone: 'UTC' });
const MONTH = new Intl.DateTimeFormat('fr-FR', {
  month: 'short',
  year: '2-digit',
  timeZone: 'UTC',
});

/** "5,2 h", "—" when not measured. */
export function formatHours(hours: number | null): string {
  return hours === null ? '—' : `${DECIMAL.format(hours)} h`;
}

/** "87,5 %", "—" when not measured. */
export function formatPercent(value: number | null): string {
  return value === null ? '—' : `${DECIMAL.format(value)} %`;
}

/** "4,3" (out of 5), "—" without ratings. */
export function formatRating(value: number | null): string {
  return value === null ? '—' : DECIMAL.format(value);
}

/** "3 h 20", "45 min", "0 min". */
export function formatMinutes(minutes: number): string {
  if (minutes < 60) {
    return `${minutes} min`;
  }
  const rest = minutes % 60;
  return rest === 0
    ? `${Math.floor(minutes / 60)} h`
    : `${Math.floor(minutes / 60)} h ${String(rest).padStart(2, '0')}`;
}

/** Relative change in %, null when there is no previous value to compare with. */
export function changePercent(current: number | null, previous: number | null): number | null {
  if (current === null || previous === null || previous === 0) {
    return null;
  }
  return ((current - previous) / previous) * 100;
}

/** Axis label of a timeline bucket: "3 nov." per day or week, "nov. 26" per month. */
export function bucketLabel(bucket: string, granularity: 'DAY' | 'WEEK' | 'MONTH'): string {
  const date = new Date(`${bucket}T00:00:00Z`);
  return granularity === 'MONTH' ? MONTH.format(date) : DAY.format(date);
}

function status(value: string | null): string {
  return value && value in STATUS_LABELS ? STATUS_LABELS[value as TicketStatus] : (value ?? '');
}

/** One line of the activity feed: "Karim Belhadj a passé le ticket au statut « Résolu »". */
export function describeActivity(item: ActivityItem): string {
  const who = item.actorName ?? 'NexaDesk';
  switch (item.action) {
    case 'CREATED':
      return `${who} a créé le ticket`;
    case 'STATUS_CHANGED':
      return `${who} a passé le ticket au statut « ${status(item.newValue)} »`;
    case 'REOPENED':
      return `${who} a rouvert le ticket`;
    case 'ASSIGNED':
      return `${who} a affecté le ticket à ${item.newValue ?? 'un technicien'}`;
    case 'REASSIGNED':
      return `${who} a réaffecté le ticket à ${item.newValue ?? 'un technicien'}`;
    case 'PRIORITY_CHANGED':
      return `${who} a changé la priorité : ${item.newValue ?? ''}`;
    case 'CATEGORY_CHANGED':
      return `${who} a changé la catégorie : ${item.newValue ?? ''}`;
    case 'UPDATED':
      return `${who} a modifié le ticket`;
    case 'COMMENT_ADDED':
      return `${who} a commenté le ticket`;
    case 'ATTACHMENT_ADDED':
      return `${who} a ajouté une pièce jointe`;
    case 'ATTACHMENT_REMOVED':
      return `${who} a retiré une pièce jointe`;
    case 'INTERVENTION_ADDED':
    case 'INTERVENTION_UPDATED':
      return `${who} a enregistré une intervention`;
    case 'FEEDBACK_ADDED':
      return `${who} a évalué le ticket${item.newValue ? ` (${item.newValue})` : ''}`;
    case 'SLA_WARNING':
      return 'Le SLA du ticket arrive à échéance';
    case 'SLA_BREACHED':
      return 'Le SLA du ticket est dépassé';
    default:
      return `${who} a modifié le ticket`;
  }
}
