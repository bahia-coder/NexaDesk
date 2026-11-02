import {
  CategoryRef,
  Instant,
  PriorityCode,
  PriorityRef,
  SlaView,
  TicketStatus,
  TicketType,
  UserRef,
} from '../../core/models/ticket.models';

// Mirrors com.nexadesk.ticket.dto.TicketDtos and the reference data used by the ticket screens.

export type Level = 'LOW' | 'MEDIUM' | 'HIGH';

export type PendingReason = 'WAITING_USER' | 'WAITING_VENDOR' | 'WAITING_PART' | 'OTHER';

/** What the current user may do on a ticket; the API sends this subset with each ticket. */
export type TicketAction =
  | 'EDIT'
  | 'RECLASSIFY'
  | 'TAKE'
  | 'ASSIGN'
  | 'START'
  | 'PUT_ON_HOLD'
  | 'RESUME'
  | 'RESOLVE'
  | 'REJECT'
  | 'CLOSE'
  | 'CONFIRM_RESOLUTION'
  | 'REJECT_RESOLUTION'
  | 'REOPEN'
  | 'COMMENT'
  | 'COMMENT_INTERNAL'
  | 'ADD_ATTACHMENT'
  | 'LOG_INTERVENTION'
  | 'GIVE_FEEDBACK';

export type TicketScope = 'ALL' | 'MINE' | 'ASSIGNED_TO_ME' | 'UNASSIGNED';
export type SlaFilter = 'AT_RISK' | 'BREACHED';

export interface EquipmentRef {
  id: number;
  inventoryCode: string;
  name: string;
  type: string;
  location: string | null;
}

export interface Feedback {
  rating: number;
  comment: string | null;
  createdAt: Instant;
}

export interface TicketDetail {
  id: number;
  reference: string;
  type: TicketType;
  title: string;
  description: string;
  status: TicketStatus;
  impact: Level;
  urgency: Level;
  priority: PriorityRef;
  category: CategoryRef;
  subcategory: { id: number; name: string } | null;
  requester: UserRef;
  assignee: UserRef | null;
  equipment: EquipmentRef | null;
  pendingReason: PendingReason | null;
  rejectionReason: string | null;
  resolution: string | null;
  resolvedAt: Instant | null;
  closedAt: Instant | null;
  reopenCount: number;
  sla: SlaView;
  /** Optimistic locking: sent back with an edit, a stale copy is refused (409). */
  version: number;
  createdAt: Instant;
  updatedAt: Instant;
  feedback: Feedback | null;
  availableActions: TicketAction[];
}

export interface Comment {
  id: number;
  author: UserRef;
  body: string;
  internal: boolean;
  createdAt: Instant;
}

export interface Attachment {
  id: number;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  uploadedBy: UserRef;
  createdAt: Instant;
}

export interface HistoryEntry {
  id: number;
  actor: UserRef | null;
  action: string;
  field: string | null;
  oldValue: string | null;
  newValue: string | null;
  comment: string | null;
  createdAt: Instant;
}

/** List filters, all optional; kept in the URL. */
export interface TicketFilter {
  search?: string;
  status?: TicketStatus[];
  priority?: PriorityCode[];
  type?: TicketType;
  categoryId?: number;
  scope?: TicketScope;
  sla?: SlaFilter;
}

export interface Category {
  id: number;
  name: string;
  description: string | null;
  icon: string;
  active: boolean;
  subcategories: { id: number; name: string; active: boolean }[];
}

export interface Technician {
  id: number;
  fullName: string;
  email: string;
  jobTitle: string | null;
  avatarColor: string;
  specialties: CategoryRef[];
  openTickets: number;
}

export interface CreateTicketRequest {
  type: TicketType;
  title: string;
  description: string;
  categoryId: number;
  subcategoryId: number | null;
  impact: Level;
  urgency: Level;
  equipmentId: number | null;
}

/** Body of POST /tickets/{id}/status. */
export interface StatusChange {
  targetStatus: TicketStatus;
  reason?: string;
  pendingReason?: PendingReason;
  resolution?: string;
}
