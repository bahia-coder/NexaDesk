import { Dialog } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import {
  LucideAngularModule,
  RefreshCw,
  Search,
  SearchX,
  TriangleAlert,
  UserPlus,
  X,
} from 'lucide-angular';
import { Subject, catchError, debounceTime, distinctUntilChanged, map, of } from 'rxjs';

import { formatDateTime, formatRelative } from '../../core/format/time';
import { dataOf, loadState } from '../../core/http/load-state';
import { ROLE_LABELS, Role } from '../../core/models/user.models';
import { ClockService } from '../../core/services/clock.service';
import { AvatarComponent } from '../../shared/ui/avatar.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PaginatorComponent } from '../../shared/ui/paginator.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { TemporaryPasswordDialogComponent } from './temporary-password-dialog.component';
import { UserFormDialogComponent, UserFormDialogData } from './user-form-dialog.component';
import {
  ACCOUNT_STATE_LABELS,
  AccountState,
  Department,
  TemporaryPassword,
  User,
  UserFilter,
  accountState,
} from './users.models';
import { UsersService } from './users.service';

const ROLES: Role[] = ['EMPLOYEE', 'TECHNICIAN', 'ADMIN'];
const PAGE_SIZE = 20;

function filterFromParams(p: Params): UserFilter & { page: number } {
  return {
    search: typeof p['search'] === 'string' && p['search'].trim() ? p['search'].trim() : undefined,
    role: ROLES.find((r) => r === p['role']),
    active: p['active'] === 'true' ? true : p['active'] === 'false' ? false : undefined,
    departmentId: Number(p['departmentId']) > 0 ? Number(p['departmentId']) : undefined,
    page: Math.max(0, Number(p['page']) || 0),
  };
}

/** Accounts of the company (admins): search, filters, creation; a row opens the account. */
@Component({
  selector: 'nx-users-page',
  imports: [
    RouterLink,
    LucideAngularModule,
    AvatarComponent,
    EmptyStateComponent,
    PaginatorComponent,
    SkeletonComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './users.page.html',
  styleUrl: './users.scss',
})
export class UsersPage {
  private readonly api = inject(UsersService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(Dialog);
  private readonly clock = inject(ClockService);

  protected readonly icons = { RefreshCw, Search, SearchX, TriangleAlert, UserPlus, X };
  protected readonly roles = ROLES;
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly stateLabels = ACCOUNT_STATE_LABELS;

  protected readonly filter = toSignal(this.route.queryParams.pipe(map(filterFromParams)), {
    initialValue: filterFromParams({}),
  });
  protected readonly filtered = computed(() => {
    const f = this.filter();
    return !!(f.search || f.role || f.active !== undefined || f.departmentId);
  });
  protected readonly departments = toSignal(
    this.api.departments().pipe(catchError(() => of([] as Department[]))),
    { initialValue: [] as Department[] },
  );

  private readonly reload = signal(0);
  protected readonly state = loadState(
    computed(() => ({ f: this.filter(), attempt: this.reload() })),
    ({ f }) => {
      const { page, ...filter } = f;
      return this.api.list(filter, page, PAGE_SIZE, 'lastName,asc');
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
      .open<TemporaryPassword, UserFormDialogData>(UserFormDialogComponent, {
        data: { departments: this.departments() },
        ariaLabelledBy: 'nx-user-form-title',
        panelClass: 'nx-dialog-panel',
        backdropClass: 'nx-backdrop',
      })
      .closed.subscribe((created) => {
        if (created) {
          this.reload.update((n) => n + 1);
          this.dialog.open(TemporaryPasswordDialogComponent, {
            data: created,
            ariaLabelledBy: 'nx-temp-password-title',
            panelClass: 'nx-dialog-panel',
            backdropClass: 'nx-backdrop',
            disableClose: true,
          });
        }
      });
  }

  protected open(user: User): void {
    void this.router.navigate(['/users', user.id]);
  }

  protected stateOf(user: User): AccountState {
    return accountState(user, this.clock.now());
  }

  protected relative(at: string | null): string {
    return at ? formatRelative(at, this.clock.now()) : 'Jamais';
  }

  protected exact(at: string | null): string | null {
    return at ? formatDateTime(at) : null;
  }

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }
}
