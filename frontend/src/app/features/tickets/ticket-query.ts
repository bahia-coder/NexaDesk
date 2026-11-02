import { Params } from '@angular/router';

import { PriorityCode, TicketStatus, TicketType } from '../../core/models/ticket.models';
import { SlaFilter, TicketFilter, TicketScope } from './tickets.models';

// The ticket list state lives in the URL (filters, sort, page, view): a filtered view can be bookmarked or
// shared, and survives a reload. Pure conversion both ways, unit-tested.

export type TicketView = 'table' | 'board';

export interface TicketQuery {
  filter: TicketFilter;
  /** "createdAt,desc" (API sort syntax). */
  sort: string;
  page: number;
  size: number;
  view: TicketView;
}

export const PAGE_SIZES = [10, 20, 50] as const;
export const DEFAULT_SORT = 'createdAt,desc';
/** Fields the API accepts for sorting (backend whitelist). */
export const SORTABLE = [
  'reference',
  'title',
  'priority.rank',
  'status',
  'resolutionDueAt',
  'createdAt',
  'updatedAt',
] as const;

const STATUSES: TicketStatus[] = [
  'NEW',
  'ASSIGNED',
  'IN_PROGRESS',
  'PENDING',
  'RESOLVED',
  'CLOSED',
  'REJECTED',
];
const PRIORITIES: PriorityCode[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
const TYPES: TicketType[] = ['INCIDENT', 'SERVICE_REQUEST'];
const SCOPES: TicketScope[] = ['ALL', 'MINE', 'ASSIGNED_TO_ME', 'UNASSIGNED'];
const SLAS: SlaFilter[] = ['AT_RISK', 'BREACHED'];

function list(value: unknown): string[] {
  return value === undefined || value === null
    ? []
    : Array.isArray(value)
      ? value
      : [String(value)];
}

function oneOf<T extends string>(value: unknown, allowed: readonly T[]): T | undefined {
  return allowed.find((item) => item === value);
}

function positive(value: unknown, fallback: number): number {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : fallback;
}

/** Reads the URL; unknown or malformed values are ignored rather than sent to the API. */
export function queryFromParams(params: Params): TicketQuery {
  const [field, direction] = String(params['sort'] ?? DEFAULT_SORT).split(',');
  const sort =
    (SORTABLE as readonly string[]).includes(field) && (direction === 'asc' || direction === 'desc')
      ? `${field},${direction}`
      : DEFAULT_SORT;
  const size = positive(params['size'], 20);
  const categoryId = positive(params['categoryId'], 0);
  return {
    filter: {
      search:
        typeof params['search'] === 'string' && params['search'].trim()
          ? params['search'].trim()
          : undefined,
      status: list(params['status']).filter((s): s is TicketStatus =>
        STATUSES.includes(s as TicketStatus),
      ),
      priority: list(params['priority']).filter((p): p is PriorityCode =>
        PRIORITIES.includes(p as PriorityCode),
      ),
      type: oneOf(params['type'], TYPES),
      categoryId: categoryId > 0 ? categoryId : undefined,
      scope: oneOf(params['scope'], SCOPES),
      sla: oneOf(params['sla'], SLAS),
    },
    sort,
    page: positive(params['page'], 0),
    size: (PAGE_SIZES as readonly number[]).includes(size) ? size : 20,
    view: params['view'] === 'board' ? 'board' : 'table',
  };
}

/** Writes the URL, leaving out defaults so links stay short. */
export function paramsFromQuery(query: TicketQuery): Params {
  const f = query.filter;
  return {
    search: f.search || null,
    status: f.status?.length ? f.status : null,
    priority: f.priority?.length ? f.priority : null,
    type: f.type ?? null,
    categoryId: f.categoryId ?? null,
    scope: f.scope ?? null,
    sla: f.sla ?? null,
    sort: query.sort === DEFAULT_SORT ? null : query.sort,
    page: query.page > 0 ? query.page : null,
    size: query.size === 20 ? null : query.size,
    view: query.view === 'board' ? 'board' : null,
  };
}

export function activeFilterCount(filter: TicketFilter): number {
  return [
    filter.search,
    filter.status?.length,
    filter.priority?.length,
    filter.type,
    filter.categoryId,
    filter.scope,
    filter.sla,
  ].filter(Boolean).length;
}

/** Clicking a column: sort by it ascending, then descending. */
export function nextSort(current: string, field: string): string {
  const [currentField, direction] = current.split(',');
  return currentField === field && direction === 'asc' ? `${field},desc` : `${field},asc`;
}
