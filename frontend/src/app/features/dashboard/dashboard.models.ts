import { Instant, TicketSummary } from '../../core/models/ticket.models';

// Mirrors com.nexadesk.dashboard.dto.DashboardDtos and com.nexadesk.reporting.dto.AnalyticsDtos.

/** Period lengths accepted by GET /api/dashboard/admin?days= */
export const PERIODS = [7, 30, 90, 365] as const;
export type PeriodDays = (typeof PERIODS)[number];

export interface KpiValue {
  value: number;
  /** % change against the previous period of the same length; null when not comparable. */
  changePercent: number | null;
}

export interface CountItem {
  key: string;
  label: string;
  color: string | null;
  count: number;
}

export interface TimelinePoint {
  /** First day of the bucket (yyyy-MM-dd). */
  bucket: string;
  created: number;
  resolved: number;
}

export interface SlaCompliance {
  firstResponseRate: number | null;
  resolutionRate: number | null;
  resolvedMeasured: number;
}

export interface Satisfaction {
  average: number | null;
  count: number;
  /** Number of 1, 2, 3, 4 and 5-star ratings. */
  distribution: number[];
}

export interface TechnicianPerformance {
  id: number;
  fullName: string;
  avatarColor: string;
  openAssigned: number;
  resolved: number;
  avgResolutionHours: number | null;
  slaRate: number | null;
  avgRating: number | null;
  minutesLogged: number;
}

/** A ticket history entry (action = backend HistoryAction name). */
export interface ActivityItem {
  at: Instant;
  action: string;
  ticketId: number;
  ticketReference: string;
  actorName: string | null;
  field: string | null;
  oldValue: string | null;
  newValue: string | null;
  comment: string | null;
}

export interface AdminDashboard {
  period: { from: string; to: string; days: number; granularity: 'DAY' | 'WEEK' | 'MONTH' };
  kpis: {
    created: KpiValue;
    resolved: KpiValue;
    open: number;
    inProgress: number;
    pending: number;
    overdue: number;
    atRisk: number;
    unassigned: number;
    avgResolutionHours: number | null;
    avgResolutionHoursPrevious: number | null;
    resolutionRate: number | null;
  };
  sla: SlaCompliance;
  timeline: TimelinePoint[];
  byPriority: CountItem[];
  byStatus: CountItem[];
  byCategory: CountItem[];
  technicians: TechnicianPerformance[];
  satisfaction: Satisfaction;
  recentActivity: ActivityItem[];
}

export interface TechnicianDashboard {
  assignedOpen: number;
  pending: number;
  urgent: number;
  overdue: number;
  atRisk: number;
  resolvedThisWeek: number;
  unassignedInSpecialties: number;
  minutesThisWeek: number;
  avgResolutionHours: number | null;
  slaRate: number | null;
  byCategory: CountItem[];
  queue: TicketSummary[];
  recentActivity: ActivityItem[];
}

export interface EmployeeDashboard {
  waiting: number;
  inProgress: number;
  resolved: number;
  toConfirm: number;
  awaitingConfirmation: TicketSummary[];
  recent: TicketSummary[];
}
