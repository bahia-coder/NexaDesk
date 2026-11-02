import { PriorityCode, TicketStatus } from '../models/ticket.models';

/** Badge tones, mapped to `.badge--*` classes of the design system. */
export type Tone =
  'info' | 'success' | 'warning' | 'danger' | 'indigo' | 'violet' | 'blue' | 'orange' | 'slate';

export const STATUS_LABELS: Record<TicketStatus, string> = {
  NEW: 'Nouveau',
  ASSIGNED: 'Assigné',
  IN_PROGRESS: 'En cours',
  PENDING: 'En attente',
  RESOLVED: 'Résolu',
  CLOSED: 'Fermé',
  REJECTED: 'Rejeté',
};

export const STATUS_TONES: Record<TicketStatus, Tone> = {
  NEW: 'info',
  ASSIGNED: 'indigo',
  IN_PROGRESS: 'violet',
  PENDING: 'warning',
  RESOLVED: 'success',
  CLOSED: 'slate',
  REJECTED: 'danger',
};

/** Fallback labels; the API also sends the admin-editable label with each priority. */
export const PRIORITY_LABELS: Record<PriorityCode, string> = {
  LOW: 'Basse',
  MEDIUM: 'Moyenne',
  HIGH: 'Haute',
  CRITICAL: 'Critique',
};

export const PRIORITY_TONES: Record<PriorityCode, Tone> = {
  LOW: 'slate',
  MEDIUM: 'blue',
  HIGH: 'orange',
  CRITICAL: 'danger',
};
