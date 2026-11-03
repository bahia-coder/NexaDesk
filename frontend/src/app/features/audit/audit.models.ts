import { Tone } from '../../core/format/labels';
import { Instant, UserRef } from '../../core/models/ticket.models';

// Mirrors com.nexadesk.audit.dto.AuditDtos and AuditAction.

export type AuditAction =
  | 'LOGIN_SUCCESS'
  | 'LOGIN_FAILURE'
  | 'ACCOUNT_LOCKED'
  | 'PASSWORD_CHANGED'
  | 'USER_CREATED'
  | 'USER_UPDATED'
  | 'USER_ROLE_CHANGED'
  | 'USER_DEACTIVATED'
  | 'USER_ACTIVATED'
  | 'USER_PASSWORD_RESET'
  | 'USER_SPECIALTIES_CHANGED'
  | 'TICKET_CREATED'
  | 'TICKET_UPDATED'
  | 'TICKET_RECLASSIFIED'
  | 'TICKET_ASSIGNED'
  | 'TICKET_STATUS_CHANGED'
  | 'TICKET_COMMENTED'
  | 'CATEGORY_CREATED'
  | 'CATEGORY_UPDATED'
  | 'CATEGORY_STATUS_CHANGED'
  | 'PRIORITY_UPDATED'
  | 'SLA_RULE_UPDATED'
  | 'SLA_CALENDAR_UPDATED'
  | 'HOLIDAY_ADDED'
  | 'HOLIDAY_REMOVED'
  | 'EQUIPMENT_CREATED'
  | 'EQUIPMENT_UPDATED'
  | 'EQUIPMENT_ASSIGNED'
  | 'EQUIPMENT_STATUS_CHANGED'
  | 'EQUIPMENT_RETIRED'
  | 'ARTICLE_CREATED'
  | 'ARTICLE_UPDATED'
  | 'ARTICLE_PUBLISHED'
  | 'ARTICLE_ARCHIVED';

export interface AuditEntry {
  id: number;
  /** Null when nobody was signed in (a failed login with an unknown e-mail). */
  actor: UserRef | null;
  actorEmail: string | null;
  action: AuditAction;
  entityType: string;
  /** User id, ticket reference, inventory code… depending on the entity. */
  entityId: string | null;
  summary: string;
  oldValue: Record<string, unknown> | null;
  newValue: Record<string, unknown> | null;
  ipAddress: string | null;
  traceId: string | null;
  createdAt: Instant;
}

export interface AuditFilter {
  actorId?: number;
  action?: AuditAction;
  entityType?: string;
  entityId?: string;
  /** yyyy-MM-dd, in the company time zone. */
  from?: string;
  to?: string;
}

export const AUDIT_ACTION_LABELS: Record<AuditAction, string> = {
  LOGIN_SUCCESS: 'Connexion',
  LOGIN_FAILURE: 'Échec de connexion',
  ACCOUNT_LOCKED: 'Compte verrouillé',
  PASSWORD_CHANGED: 'Mot de passe changé',
  USER_CREATED: 'Compte créé',
  USER_UPDATED: 'Compte modifié',
  USER_ROLE_CHANGED: 'Rôle modifié',
  USER_DEACTIVATED: 'Compte désactivé',
  USER_ACTIVATED: 'Compte réactivé',
  USER_PASSWORD_RESET: 'Mot de passe réinitialisé',
  USER_SPECIALTIES_CHANGED: 'Spécialités modifiées',
  TICKET_CREATED: 'Ticket créé',
  TICKET_UPDATED: 'Ticket modifié',
  TICKET_RECLASSIFIED: 'Ticket reclassé',
  TICKET_ASSIGNED: 'Ticket affecté',
  TICKET_STATUS_CHANGED: 'Statut du ticket',
  TICKET_COMMENTED: 'Commentaire',
  CATEGORY_CREATED: 'Catégorie créée',
  CATEGORY_UPDATED: 'Catégorie modifiée',
  CATEGORY_STATUS_CHANGED: 'Catégorie activée / désactivée',
  PRIORITY_UPDATED: 'Priorité modifiée',
  SLA_RULE_UPDATED: 'Règle SLA modifiée',
  SLA_CALENDAR_UPDATED: 'Calendrier modifié',
  HOLIDAY_ADDED: 'Jour férié ajouté',
  HOLIDAY_REMOVED: 'Jour férié retiré',
  EQUIPMENT_CREATED: 'Équipement ajouté',
  EQUIPMENT_UPDATED: 'Équipement modifié',
  EQUIPMENT_ASSIGNED: 'Équipement affecté',
  EQUIPMENT_STATUS_CHANGED: 'État de l’équipement',
  EQUIPMENT_RETIRED: 'Équipement mis au rebut',
  ARTICLE_CREATED: 'Article rédigé',
  ARTICLE_UPDATED: 'Article modifié',
  ARTICLE_PUBLISHED: 'Article publié',
  ARTICLE_ARCHIVED: 'Article archivé',
};

/** Filter groups, in the order of the backend enum. */
export const AUDIT_ACTION_GROUPS: { label: string; actions: AuditAction[] }[] = [
  {
    label: 'Authentification',
    actions: ['LOGIN_SUCCESS', 'LOGIN_FAILURE', 'ACCOUNT_LOCKED', 'PASSWORD_CHANGED'],
  },
  {
    label: 'Utilisateurs',
    actions: [
      'USER_CREATED',
      'USER_UPDATED',
      'USER_ROLE_CHANGED',
      'USER_DEACTIVATED',
      'USER_ACTIVATED',
      'USER_PASSWORD_RESET',
      'USER_SPECIALTIES_CHANGED',
    ],
  },
  {
    label: 'Tickets',
    actions: [
      'TICKET_CREATED',
      'TICKET_UPDATED',
      'TICKET_RECLASSIFIED',
      'TICKET_ASSIGNED',
      'TICKET_STATUS_CHANGED',
      'TICKET_COMMENTED',
    ],
  },
  {
    label: 'Configuration',
    actions: [
      'CATEGORY_CREATED',
      'CATEGORY_UPDATED',
      'CATEGORY_STATUS_CHANGED',
      'PRIORITY_UPDATED',
      'SLA_RULE_UPDATED',
      'SLA_CALENDAR_UPDATED',
      'HOLIDAY_ADDED',
      'HOLIDAY_REMOVED',
    ],
  },
  {
    label: 'Équipements',
    actions: [
      'EQUIPMENT_CREATED',
      'EQUIPMENT_UPDATED',
      'EQUIPMENT_ASSIGNED',
      'EQUIPMENT_STATUS_CHANGED',
      'EQUIPMENT_RETIRED',
    ],
  },
  {
    label: 'Base de connaissances',
    actions: ['ARTICLE_CREATED', 'ARTICLE_UPDATED', 'ARTICLE_PUBLISHED', 'ARTICLE_ARCHIVED'],
  },
];

export const AUDIT_ACTIONS = AUDIT_ACTION_GROUPS.flatMap((g) => g.actions);

/** Security events stand out; the rest is routine administration. */
export function auditTone(action: AuditAction): Tone {
  switch (action) {
    case 'LOGIN_FAILURE':
    case 'ACCOUNT_LOCKED':
    case 'USER_DEACTIVATED':
    case 'EQUIPMENT_RETIRED':
      return 'danger';
    case 'USER_ROLE_CHANGED':
    case 'USER_PASSWORD_RESET':
    case 'PASSWORD_CHANGED':
      return 'warning';
    case 'LOGIN_SUCCESS':
      return 'slate';
    default:
      return 'info';
  }
}

/** Entity types written by the API (AuditService callers). */
export const ENTITY_TYPE_LABELS: Record<string, string> = {
  USER: 'Utilisateur',
  TICKET: 'Ticket',
  CATEGORY: 'Catégorie',
  PRIORITY: 'Priorité',
  SLA_RULE: 'Règle SLA',
  SLA_CALENDAR: 'Calendrier',
  HOLIDAY: 'Jour férié',
  EQUIPMENT: 'Équipement',
  ARTICLE: 'Article',
};

export interface AuditChange {
  field: string;
  before: string;
  after: string;
}

function show(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '—';
  }
  if (Array.isArray(value)) {
    return value.length ? value.map(show).join(', ') : '—';
  }
  return typeof value === 'object' ? JSON.stringify(value) : String(value);
}

/** Fields whose value differs between the snapshots, in a readable form ("—" for none). */
export function auditChanges(
  before: Record<string, unknown> | null,
  after: Record<string, unknown> | null,
): AuditChange[] {
  const keys = [...new Set([...Object.keys(before ?? {}), ...Object.keys(after ?? {})])];
  return keys
    .map((field) => ({ field, before: show(before?.[field]), after: show(after?.[field]) }))
    .filter((change) => change.before !== change.after);
}

/** Where the audited object can be opened in the application, if anywhere. */
export function auditLink(
  entry: Pick<AuditEntry, 'entityType' | 'entityId'>,
): { path: string[]; query?: Record<string, string> } | null {
  const id = entry.entityId;
  switch (entry.entityType) {
    case 'USER':
      return id && /^\d+$/.test(id) ? { path: ['/users', id] } : null;
    case 'ARTICLE':
      return id && /^\d+$/.test(id) ? { path: ['/knowledge', id] } : null;
    case 'TICKET':
      return id ? { path: ['/tickets'], query: { search: id } } : null;
    case 'EQUIPMENT':
      return id ? { path: ['/equipment'], query: { search: id } } : null;
    case 'CATEGORY':
      return { path: ['/settings'], query: { tab: 'categories' } };
    case 'PRIORITY':
      return { path: ['/settings'], query: { tab: 'priorities' } };
    case 'SLA_RULE':
    case 'SLA_CALENDAR':
    case 'HOLIDAY':
      return { path: ['/settings'] };
    default:
      return null;
  }
}
