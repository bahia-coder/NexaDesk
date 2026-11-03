import { Instant } from '../../core/models/ticket.models';
import {
  CountItem,
  Satisfaction,
  SlaCompliance,
  TechnicianPerformance,
  TimelinePoint,
} from '../dashboard/dashboard.models';

// Mirrors com.nexadesk.reporting.dto.ReportDtos.

/** All optional (yyyy-MM-dd dates): the API defaults to the last 90 days. */
export interface ReportFilter {
  from?: string;
  to?: string;
  categoryId?: number;
  technicianId?: number;
}

/** The filters the API actually applied, with display names. */
export interface ReportScope {
  from: string;
  to: string;
  categoryId: number | null;
  categoryName: string | null;
  technicianId: number | null;
  technicianName: string | null;
}

export interface ReportSummary {
  created: number;
  resolved: number;
  /** % of the tickets created in the period that are resolved. */
  resolutionRate: number | null;
  avgResolutionHours: number | null;
  sla: SlaCompliance;
}

export interface Report {
  scope: ReportScope;
  summary: ReportSummary;
  byCategory: CountItem[];
  byPriority: CountItem[];
  byTechnician: TechnicianPerformance[];
  /** One point per month of the period. */
  monthly: TimelinePoint[];
  satisfaction: Satisfaction;
  generatedAt: Instant;
}

export type ExportFormat = 'xlsx' | 'pdf';

const MAX_DAYS = 731;
const DAY_MS = 86_400_000;

/** Same limits as the API (ReportService): start before end, two years at most. */
export function reportPeriodError(from: string | null, to: string | null): string | null {
  if (!from || !to) {
    return null;
  }
  if (from > to) {
    return 'La date de début doit précéder la date de fin.';
  }
  const days = (Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / DAY_MS;
  return days >= MAX_DAYS ? 'La période est limitée à deux ans.' : null;
}
