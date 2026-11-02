import { Instant, PriorityCode } from '../../core/models/ticket.models';

// Mirrors com.nexadesk.sla.dto.SlaDtos and com.nexadesk.category.dto.CategoryDtos (admin settings).

export interface SlaRule {
  id: number;
  priority: PriorityCode;
  priorityLabel: string;
  priorityColor: string;
  firstResponseMinutes: number;
  resolutionMinutes: number;
  /** false: the clock runs 24/7. */
  businessHoursOnly: boolean;
  /** % of the resolution time after which the ticket is flagged at risk (50–95). */
  warningThresholdPercent: number;
}

export type DayOfWeek =
  'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export const DAYS: { day: DayOfWeek; label: string }[] = [
  { day: 'MONDAY', label: 'Lundi' },
  { day: 'TUESDAY', label: 'Mardi' },
  { day: 'WEDNESDAY', label: 'Mercredi' },
  { day: 'THURSDAY', label: 'Jeudi' },
  { day: 'FRIDAY', label: 'Vendredi' },
  { day: 'SATURDAY', label: 'Samedi' },
  { day: 'SUNDAY', label: 'Dimanche' },
];

export interface BusinessCalendar {
  /** Deployment setting (APP_TIMEZONE), read-only here. */
  timeZone: string;
  /** "08:00:00" */
  workStart: string;
  workEnd: string;
  workingDays: DayOfWeek[];
}

export interface Holiday {
  id: number;
  /** yyyy-MM-dd */
  day: string;
  label: string;
}

export interface SlaPreview {
  priority: PriorityCode;
  from: Instant;
  businessHoursOnly: boolean;
  firstResponseDueAt: Instant;
  resolutionDueAt: Instant;
}

export interface PriorityItem {
  id: number;
  code: PriorityCode;
  label: string;
  rank: number;
  color: string;
}

export interface MatrixEntry {
  impact: 'LOW' | 'MEDIUM' | 'HIGH';
  urgency: 'LOW' | 'MEDIUM' | 'HIGH';
  priority: PriorityCode;
}

export interface CategoryRequest {
  name: string;
  description: string | null;
  icon: string | null;
  keywords: string | null;
}

/** Same rule as the API: a Lucide icon name ("wifi", "key-round"). */
export const ICON_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;
