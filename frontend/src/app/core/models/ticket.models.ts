// Types mirroring the ticket DTOs of the API (backend: com.nexadesk.ticket.dto.TicketDtos, TicketSla.View).

export type TicketStatus =
  'NEW' | 'ASSIGNED' | 'IN_PROGRESS' | 'PENDING' | 'RESOLVED' | 'CLOSED' | 'REJECTED';

export type PriorityCode = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type SlaState = 'NONE' | 'ON_TRACK' | 'AT_RISK' | 'BREACHED' | 'PAUSED' | 'MET' | 'MISSED';

/** ISO-8601 instant as serialized by the API. */
export type Instant = string;

export interface SlaView {
  state: SlaState;
  firstResponseDueAt: Instant | null;
  firstRespondedAt: Instant | null;
  firstResponseBreached: boolean;
  resolutionDueAt: Instant | null;
  /** Share of the resolution time already consumed (business time), 0–100+; null without SLA. */
  consumedPercent: number | null;
  businessHoursOnly: boolean;
}

export interface PriorityRef {
  code: PriorityCode;
  label: string;
  color: string;
  rank: number;
}

export interface UserRef {
  id: number;
  fullName: string;
  avatarColor: string;
}
