import {
  AlarmClock,
  ArrowRightLeft,
  CircleCheck,
  LucideIconData,
  MessageSquare,
  Siren,
  ThumbsDown,
  ThumbsUp,
  TicketPlus,
  UserCheck,
} from 'lucide-angular';

import { Tone } from '../format/labels';
import { Instant } from '../models/ticket.models';

// Mirrors com.nexadesk.notification.dto.NotificationDto and NotificationType.

export type NotificationType =
  | 'TICKET_CREATED'
  | 'TICKET_ASSIGNED'
  | 'STATUS_CHANGED'
  | 'NEW_COMMENT'
  | 'SLA_WARNING'
  | 'SLA_BREACHED'
  | 'TICKET_RESOLVED'
  | 'RESOLUTION_CONFIRMED'
  | 'RESOLUTION_REJECTED';

export interface AppNotification {
  id: number;
  type: NotificationType;
  title: string;
  message: string;
  ticketId: number | null;
  /** In-app path ("/tickets/42"). */
  link: string | null;
  read: boolean;
  createdAt: Instant;
}

export const NOTIFICATION_VISUALS: Record<NotificationType, { icon: LucideIconData; tone: Tone }> =
  {
    TICKET_CREATED: { icon: TicketPlus, tone: 'info' },
    TICKET_ASSIGNED: { icon: UserCheck, tone: 'indigo' },
    STATUS_CHANGED: { icon: ArrowRightLeft, tone: 'violet' },
    NEW_COMMENT: { icon: MessageSquare, tone: 'blue' },
    SLA_WARNING: { icon: AlarmClock, tone: 'warning' },
    SLA_BREACHED: { icon: Siren, tone: 'danger' },
    TICKET_RESOLVED: { icon: CircleCheck, tone: 'success' },
    RESOLUTION_CONFIRMED: { icon: ThumbsUp, tone: 'success' },
    RESOLUTION_REJECTED: { icon: ThumbsDown, tone: 'orange' },
  };
