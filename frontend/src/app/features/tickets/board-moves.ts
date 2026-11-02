import { TicketStatus } from '../../core/models/ticket.models';
import { Role } from '../../core/models/user.models';
import { TicketAction } from './tickets.models';

export const BOARD_COLUMNS: TicketStatus[] = [
  'NEW',
  'ASSIGNED',
  'IN_PROGRESS',
  'PENDING',
  'RESOLVED',
];

/**
 * Which workflow action a drag from one column to another means (same transitions as the API's workflow).
 * Null: the move is not a workflow step (the column is greyed out while dragging). The API still decides for each
 * ticket (assignee, specialty…), so an allowed move can be refused and is then rolled back.
 */
export function moveAction(
  from: TicketStatus,
  to: TicketStatus,
  role: Role | null,
): TicketAction | null {
  if (from === 'NEW' && to === 'ASSIGNED') {
    return role === 'ADMIN' ? 'ASSIGN' : role === 'TECHNICIAN' ? 'TAKE' : null;
  }
  if (from === 'ASSIGNED' && to === 'IN_PROGRESS') {
    return 'START';
  }
  if (from === 'IN_PROGRESS' && to === 'PENDING') {
    return 'PUT_ON_HOLD';
  }
  if (from === 'PENDING' && to === 'IN_PROGRESS') {
    return 'RESUME';
  }
  if (from === 'IN_PROGRESS' && to === 'RESOLVED') {
    return 'RESOLVE';
  }
  return null;
}
