import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  ArrowDown,
  ArrowUp,
  ArrowUpDown,
  Columns3,
  LucideAngularModule,
  Plus,
  RefreshCw,
  Rows3,
  Search,
  SearchX,
  TriangleAlert,
  X,
} from 'lucide-angular';
import { Subject, catchError, debounceTime, distinctUntilChanged, map, of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { PRIORITY_LABELS, STATUS_LABELS } from '../../core/format/labels';
import { formatDateTime, formatRelative } from '../../core/format/time';
import { dataOf, loadState } from '../../core/http/load-state';
import { PriorityCode, TicketStatus } from '../../core/models/ticket.models';
import { ClockService } from '../../core/services/clock.service';
import { AvatarComponent } from '../../shared/ui/avatar.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PaginatorComponent } from '../../shared/ui/paginator.component';
import { PriorityBadgeComponent } from '../../shared/ui/priority-badge.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { SlaIndicatorComponent } from '../../shared/ui/sla-indicator.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { TicketBoardComponent } from './ticket-board.component';
import {
  PAGE_SIZES,
  TicketQuery,
  activeFilterCount,
  nextSort,
  paramsFromQuery,
  queryFromParams,
} from './ticket-query';
import { SCOPE_LABELS, SLA_FILTER_LABELS, TYPE_LABELS } from './tickets.labels';
import { Category, SlaFilter, TicketFilter, TicketScope } from './tickets.models';
import { TicketsService } from './tickets.service';

/** Ticket list: table (or Kanban for staff), filters, sort and pagination, all kept in the URL. */
@Component({
  selector: 'nx-tickets-page',
  imports: [
    RouterLink,
    LucideAngularModule,
    AvatarComponent,
    EmptyStateComponent,
    PaginatorComponent,
    PriorityBadgeComponent,
    SkeletonComponent,
    SlaIndicatorComponent,
    StatusBadgeComponent,
    TicketBoardComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './tickets.page.html',
  styleUrl: './tickets.page.scss',
})
export class TicketsPage {
  private readonly api = inject(TicketsService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly clock = inject(ClockService);
  protected readonly auth = inject(AuthService);

  protected readonly icons = {
    ArrowDown,
    ArrowUp,
    ArrowUpDown,
    Columns3,
    Plus,
    RefreshCw,
    Rows3,
    Search,
    SearchX,
    TriangleAlert,
    X,
  };
  protected readonly labels = {
    status: STATUS_LABELS,
    priority: PRIORITY_LABELS,
    type: TYPE_LABELS,
    scope: SCOPE_LABELS,
    sla: SLA_FILTER_LABELS,
  };
  protected readonly statuses = Object.keys(STATUS_LABELS) as TicketStatus[];
  protected readonly priorities = Object.keys(PRIORITY_LABELS) as PriorityCode[];
  protected readonly scopes = Object.keys(SCOPE_LABELS) as TicketScope[];
  protected readonly slaFilters = Object.keys(SLA_FILTER_LABELS) as SlaFilter[];
  protected readonly pageSizes = PAGE_SIZES;

  protected readonly staff = computed(() => this.auth.role() !== 'EMPLOYEE');
  protected readonly query = toSignal(this.route.queryParams.pipe(map(queryFromParams)), {
    initialValue: queryFromParams({}),
  });
  protected readonly filterCount = computed(() => activeFilterCount(this.query().filter));

  protected readonly categories = toSignal(
    this.api.categories().pipe(catchError(() => of([] as Category[]))),
    { initialValue: [] as Category[] },
  );

  private readonly reload = signal(0);
  protected readonly state = loadState(
    computed(() => ({ q: this.query(), attempt: this.reload() })),
    ({ q }) => this.api.list(q.filter, q.page, q.size, q.sort),
  );
  protected readonly page = computed(() => dataOf(this.state()));

  private readonly search$ = new Subject<string>();

  constructor() {
    this.search$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((search) => this.update({ search: search.trim() || undefined }));
  }

  protected onSearch(value: string): void {
    this.search$.next(value);
  }

  /** Changes one or more filters; back to the first page. */
  protected update(change: Partial<TicketFilter>): void {
    const q = this.query();
    this.navigate({ ...q, filter: { ...q.filter, ...change }, page: 0 });
  }

  protected setSelect(name: 'status' | 'priority', value: string): void {
    this.update({ [name]: value ? [value] : [] });
  }

  protected reset(): void {
    this.navigate({ ...this.query(), filter: {}, page: 0 });
  }

  protected sortBy(field: string): void {
    this.navigate({ ...this.query(), sort: nextSort(this.query().sort, field), page: 0 });
  }

  protected sortState(field: string): 'ascending' | 'descending' | 'none' {
    const [current, direction] = this.query().sort.split(',');
    return current !== field ? 'none' : direction === 'asc' ? 'ascending' : 'descending';
  }

  protected goTo(page: number): void {
    this.navigate({ ...this.query(), page });
  }

  protected resize(size: number): void {
    this.navigate({ ...this.query(), size, page: 0 });
  }

  protected setView(view: 'table' | 'board'): void {
    this.navigate({ ...this.query(), view });
  }

  protected open(id: number): void {
    void this.router.navigate(['/tickets', id]);
  }

  protected relative(at: string): string {
    return formatRelative(at, this.clock.now());
  }

  protected exact(at: string): string {
    return formatDateTime(at);
  }

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }

  private navigate(query: TicketQuery): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: paramsFromQuery(query) });
  }
}
