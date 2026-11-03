import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import {
  ChevronDown,
  ChevronRight,
  LucideAngularModule,
  RefreshCw,
  ScrollText,
  SearchX,
  TriangleAlert,
  X,
} from 'lucide-angular';
import { Subject, debounceTime, distinctUntilChanged, map } from 'rxjs';

import { formatDateTime } from '../../core/format/time';
import { dataOf, loadState } from '../../core/http/load-state';
import { AvatarComponent } from '../../shared/ui/avatar.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PaginatorComponent } from '../../shared/ui/paginator.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import {
  AUDIT_ACTIONS,
  AUDIT_ACTION_GROUPS,
  AUDIT_ACTION_LABELS,
  AuditEntry,
  AuditFilter,
  ENTITY_TYPE_LABELS,
  auditChanges,
  auditLink,
  auditTone,
} from './audit.models';
import { AuditService } from './audit.service';

const PAGE_SIZE = 50;
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

function filterFromParams(p: Params): AuditFilter & { page: number } {
  const text = (key: string) =>
    typeof p[key] === 'string' && p[key].trim() ? (p[key] as string).trim() : undefined;
  return {
    actorId: Number(p['actorId']) > 0 ? Number(p['actorId']) : undefined,
    action: AUDIT_ACTIONS.find((a) => a === p['action']),
    entityType: p['entityType'] in ENTITY_TYPE_LABELS ? (p['entityType'] as string) : undefined,
    entityId: text('entityId'),
    from: ISO_DATE.test(p['from'] ?? '') ? p['from'] : undefined,
    to: ISO_DATE.test(p['to'] ?? '') ? p['to'] : undefined,
    page: Math.max(0, Number(p['page']) || 0),
  };
}

/**
 * Audit journal (admins): who did what, when, from where, with the values before and after. Filters in the
 * URL; a click on a person filters on their actions.
 */
@Component({
  selector: 'nx-audit-page',
  imports: [
    RouterLink,
    LucideAngularModule,
    AvatarComponent,
    EmptyStateComponent,
    PaginatorComponent,
    SkeletonComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './audit.page.html',
  styleUrls: ['../users/users.scss', './audit.scss'],
})
export class AuditPage {
  private readonly api = inject(AuditService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly icons = {
    ChevronDown,
    ChevronRight,
    RefreshCw,
    ScrollText,
    SearchX,
    TriangleAlert,
    X,
  };
  protected readonly groups = AUDIT_ACTION_GROUPS;
  protected readonly actionLabels = AUDIT_ACTION_LABELS;
  protected readonly entityLabels = ENTITY_TYPE_LABELS;
  protected readonly entityTypes = Object.keys(ENTITY_TYPE_LABELS);

  protected readonly filter = toSignal(this.route.queryParams.pipe(map(filterFromParams)), {
    initialValue: filterFromParams({}),
  });
  protected readonly filtered = computed(() => {
    const { page, ...f } = this.filter();
    return Object.values(f).some((v) => v !== undefined);
  });

  private readonly reload = signal(0);
  protected readonly state = loadState(
    computed(() => ({ f: this.filter(), attempt: this.reload() })),
    ({ f }) => {
      const { page, ...filter } = f;
      return this.api.search(filter, page, PAGE_SIZE);
    },
  );
  protected readonly page = computed(() => dataOf(this.state()));
  /** Name of the person filtered on, read from the results. */
  protected readonly actorName = computed(() => {
    const id = this.filter().actorId;
    return id
      ? (this.page()?.content.find((e) => e.actor?.id === id)?.actor?.fullName ?? null)
      : null;
  });

  protected readonly open = signal<number | null>(null);
  protected readonly periodError = signal<string | null>(null);
  private readonly entityId$ = new Subject<string>();

  constructor() {
    this.entityId$
      .pipe(debounceTime(400), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((value) => this.update({ entityId: value.trim() || null }));
  }

  protected onEntityId(value: string): void {
    this.entityId$.next(value);
  }

  protected setPeriod(from: string, to: string): void {
    // The journal has no length limit: only the order of the dates matters.
    const error = from && to && from > to ? 'La date de début doit précéder la date de fin.' : null;
    this.periodError.set(error);
    if (!error) {
      this.update({ from: from || null, to: to || null });
    }
  }

  protected update(change: Params): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { ...change, page: null },
      queryParamsHandling: 'merge',
    });
  }

  protected reset(): void {
    this.periodError.set(null);
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  protected goTo(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: page || null },
      queryParamsHandling: 'merge',
    });
  }

  protected toggle(entry: AuditEntry): void {
    this.open.update((id) => (id === entry.id ? null : entry.id));
  }

  protected hasDetails(entry: AuditEntry): boolean {
    return !!(entry.oldValue || entry.newValue || entry.ipAddress || entry.traceId);
  }

  protected changes(entry: AuditEntry) {
    return auditChanges(entry.oldValue, entry.newValue);
  }

  protected link(entry: AuditEntry) {
    return auditLink(entry);
  }

  /** The API may add an action or an entity type before the interface knows its label. */
  protected actionLabel(entry: AuditEntry): string {
    return (AUDIT_ACTION_LABELS as Partial<Record<string, string>>)[entry.action] ?? entry.action;
  }

  protected entityLabel(entry: AuditEntry): string {
    return (
      (ENTITY_TYPE_LABELS as Partial<Record<string, string>>)[entry.entityType] ?? entry.entityType
    );
  }

  protected tone(entry: AuditEntry): string {
    return 'badge badge--' + auditTone(entry.action);
  }

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }

  protected exact(at: string): string {
    return formatDateTime(at);
  }
}
