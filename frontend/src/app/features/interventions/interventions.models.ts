import { Page } from '../../core/models/page.models';
import { Instant, UserRef } from '../../core/models/ticket.models';

// Mirrors com.nexadesk.intervention.dto.InterventionDtos.

export interface Intervention {
  id: number;
  ticketId: number;
  ticketReference: string;
  ticketTitle: string;
  technician: UserRef;
  diagnosis: string;
  solution: string | null;
  timeSpentMinutes: number;
  startedAt: Instant | null;
  endedAt: Instant | null;
  createdAt: Instant;
  updatedAt: Instant;
  /** The current user may still change it (author or admin, ticket not closed). */
  editable: boolean;
}

export interface TicketInterventions {
  interventions: Intervention[];
  totalMinutes: number;
}

export interface MyInterventions {
  page: Page<Intervention>;
  totalMinutes: number;
}

export interface InterventionRequest {
  diagnosis: string;
  solution: string | null;
  timeSpentMinutes: number;
}
