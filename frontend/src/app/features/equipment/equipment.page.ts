import { Dialog } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import {
  LucideAngularModule,
  Monitor,
  Plus,
  RefreshCw,
  Search,
  SearchX,
  TriangleAlert,
  X,
} from 'lucide-angular';
import { Subject, debounceTime, distinctUntilChanged, map } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { dataOf, loadState } from '../../core/http/load-state';
import { AvatarComponent } from '../../shared/ui/avatar.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PaginatorComponent } from '../../shared/ui/paginator.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import {
  EquipmentFormDialogComponent,
  EquipmentFormDialogData,
} from './equipment-form-dialog.component';
import {
  EQUIPMENT_STATUSES,
  EQUIPMENT_STATUS_LABELS,
  EQUIPMENT_STATUS_TONES,
  EQUIPMENT_TYPES,
  EQUIPMENT_TYPE_ICONS,
  EQUIPMENT_TYPE_LABELS,
  Equipment,
  EquipmentFilter,
} from './equipment.models';
import { EquipmentService } from './equipment.service';

const PAGE_SIZE = 20;

function filterFromParams(p: Params): EquipmentFilter & { page: number } {
  return {
    search: typeof p['search'] === 'string' && p['search'].trim() ? p['search'].trim() : undefined,
    type: EQUIPMENT_TYPES.find((t) => t === p['type']),
    status: EQUIPMENT_STATUSES.find((s) => s === p['status']),
    page: Math.max(0, Number(p['page']) || 0),
  };
}

/** Hardware inventory (support staff), or the devices an employee holds. Filters in the URL. */
@Component({
  selector: 'nx-equipment-page',
  imports: [
    RouterLink,
    LucideAngularModule,
    AvatarComponent,
    EmptyStateComponent,
    PaginatorComponent,
    SkeletonComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './equipment.page.html',
  styleUrl: './equipment.scss',
})
export class EquipmentPage {
  private readonly api = inject(EquipmentService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(Dialog);

  protected readonly icons = { Monitor, Plus, RefreshCw, Search, SearchX, TriangleAlert, X };
  protected readonly types = EQUIPMENT_TYPES;
  protected readonly statuses = EQUIPMENT_STATUSES;
  protected readonly typeLabels = EQUIPMENT_TYPE_LABELS;
  protected readonly typeIcons = EQUIPMENT_TYPE_ICONS;
  protected readonly statusLabels = EQUIPMENT_STATUS_LABELS;
  protected readonly statusTones = EQUIPMENT_STATUS_TONES;

  protected readonly employee = computed(() => this.auth.role() === 'EMPLOYEE');
  protected readonly admin = computed(() => this.auth.role() === 'ADMIN');

  protected readonly filter = toSignal(this.route.queryParams.pipe(map(filterFromParams)), {
    initialValue: filterFromParams({}),
  });
  protected readonly filtered = computed(() => {
    const f = this.filter();
    return !!(f.search || f.type || f.status);
  });

  private readonly reload = signal(0);
  protected readonly state = loadState(
    computed(() => ({ f: this.filter(), attempt: this.reload() })),
    ({ f }) => {
      const { page, ...filter } = f;
      return this.api.list(filter, page, PAGE_SIZE);
    },
  );
  protected readonly page = computed(() => dataOf(this.state()));

  private readonly search$ = new Subject<string>();

  constructor() {
    this.search$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((search) => this.update({ search: search.trim() || null }));
  }

  protected onSearch(value: string): void {
    this.search$.next(value);
  }

  protected update(change: Params): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { ...change, page: null },
      queryParamsHandling: 'merge',
    });
  }

  protected reset(): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  protected goTo(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: page || null },
      queryParamsHandling: 'merge',
    });
  }

  protected create(): void {
    this.dialog
      .open<Equipment, EquipmentFormDialogData>(EquipmentFormDialogComponent, {
        data: { equipment: null },
        ariaLabelledBy: 'nx-equipment-form-title',
        panelClass: 'nx-dialog-panel',
        backdropClass: 'nx-backdrop',
      })
      .closed.subscribe((created) => {
        if (created) {
          void this.router.navigate(['/equipment', created.id]);
        }
      });
  }

  protected open(equipment: Equipment): void {
    void this.router.navigate(['/equipment', equipment.id]);
  }

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }

  protected exactDate(day: string | null): string {
    return day ? day.split('-').reverse().join('/') : '—';
  }
}
