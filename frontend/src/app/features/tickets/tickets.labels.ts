import { PriorityCode, TicketType } from '../../core/models/ticket.models';
import { Level, PendingReason, SlaFilter, TicketScope } from './tickets.models';

export const TYPE_LABELS: Record<TicketType, string> = {
  INCIDENT: 'Incident',
  SERVICE_REQUEST: 'Demande de service',
};

export const PENDING_LABELS: Record<PendingReason, string> = {
  WAITING_USER: 'Réponse du demandeur',
  WAITING_VENDOR: 'Intervention d’un fournisseur',
  WAITING_PART: 'Pièce ou matériel',
  OTHER: 'Autre motif',
};

export const IMPACT_LABELS: Record<Level, string> = {
  LOW: 'Moi seul',
  MEDIUM: 'Mon équipe',
  HIGH: 'Tout le service ou plus',
};

export const URGENCY_LABELS: Record<Level, string> = {
  LOW: 'Peut attendre',
  MEDIUM: 'Gêne mon travail',
  HIGH: 'Bloquant',
};

export const SCOPE_LABELS: Record<TicketScope, string> = {
  ALL: 'Tous les tickets visibles',
  MINE: 'Mes demandes',
  ASSIGNED_TO_ME: 'Assignés à moi',
  UNASSIGNED: 'Non assignés',
};

export const SLA_FILTER_LABELS: Record<SlaFilter, string> = {
  AT_RISK: 'SLA à risque',
  BREACHED: 'SLA dépassé',
};

/**
 * ITIL impact × urgency matrix, same as the API (backend: category.PriorityMatrix). Shown while the user fills the
 * form; the server computes the stored priority.
 */
export function priorityFor(impact: Level, urgency: Level): PriorityCode {
  const rank = { LOW: 0, MEDIUM: 1, HIGH: 2 };
  const score = rank[impact] + rank[urgency];
  return score <= 1 ? 'LOW' : score === 2 ? 'MEDIUM' : score === 3 ? 'HIGH' : 'CRITICAL';
}

/** "1,2 Mo", "85 Ko". */
export function formatSize(bytes: number): string {
  const format = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 });
  return bytes >= 1024 * 1024
    ? `${format.format(bytes / (1024 * 1024))} Mo`
    : `${format.format(Math.max(1, bytes / 1024))} Ko`;
}
