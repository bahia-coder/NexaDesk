import {
  ChangeDetectionStrategy,
  Component,
  DOCUMENT,
  computed,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params, Router } from '@angular/router';
import {
  ChartColumn,
  CircleCheck,
  Clock,
  FileSpreadsheet,
  FileText,
  LucideAngularModule,
  Percent,
  RefreshCw,
  Star,
  Ticket,
  TriangleAlert,
  X,
} from 'lucide-angular';
import { catchError, map, of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { formatDateTime } from '../../core/format/time';
import { toApiProblem } from '../../core/http/api-error';
import { dataOf, loadState } from '../../core/http/load-state';
import { ToastService } from '../../core/services/toast.service';
import { AvatarComponent } from '../../shared/ui/avatar.component';
import { BarListComponent, BarListItem } from '../../shared/ui/bar-list.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { SpinnerComponent } from '../../shared/ui/spinner.component';
import { StatCardComponent } from '../../shared/ui/stat-card.component';
import {
  formatHours,
  formatMinutes,
  formatPercent,
  formatRating,
} from '../dashboard/dashboard.format';
import { CountItem } from '../dashboard/dashboard.models';
import { TimelineChartComponent } from '../dashboard/timeline-chart.component';
import { Category } from '../tickets/tickets.models';
import { TicketsService } from '../tickets/tickets.service';
import { User } from '../users/users.models';
import { UsersService } from '../users/users.service';
import { ExportFormat, ReportFilter, reportPeriodError } from './reports.models';
import { ExportedFile, ReportsService } from './reports.service';

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

function filterFromParams(p: Params): ReportFilter {
  return {
    from: ISO_DATE.test(p['from'] ?? '') ? p['from'] : undefined,
    to: ISO_DATE.test(p['to'] ?? '') ? p['to'] : undefined,
    categoryId: Number(p['categoryId']) > 0 ? Number(p['categoryId']) : undefined,
    technicianId: Number(p['technicianId']) > 0 ? Number(p['technicianId']) : undefined,
  };
}

function bars(items: CountItem[]): BarListItem[] {
  return items.map((item) => ({ label: item.label, value: item.count, color: item.color }));
}

/**
 * Activity report over a chosen period (admins: whole support, filterable by technician, with Excel / PDF
 * exports; technicians: their own figures). Filters in the URL so a report can be shared.
 */
@Component({
  selector: 'nx-reports-page',
  imports: [
    LucideAngularModule,
    AvatarComponent,
    BarListComponent,
    EmptyStateComponent,
    SkeletonComponent,
    SpinnerComponent,
    StatCardComponent,
    TimelineChartComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reports.page.html',
  styleUrls: ['../dashboard/dashboard.scss', './reports.scss'],
})
export class ReportsPage {
  private readonly api = inject(ReportsService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toasts = inject(ToastService);
  private readonly document = inject(DOCUMENT);

  protected readonly icons = {
    ChartColumn,
    CircleCheck,
    Clock,
    FileSpreadsheet,
    FileText,
    Percent,
    RefreshCw,
    Star,
    Ticket,
    TriangleAlert,
    X,
  };
  protected readonly format = { formatHours, formatMinutes, formatPercent, formatRating };

  protected readonly admin = computed(() => this.auth.role() === 'ADMIN');

  protected readonly filter = toSignal(this.route.queryParams.pipe(map(filterFromParams)), {
    initialValue: filterFromParams({}),
  });
  protected readonly filtered = computed(() => {
    const f = this.filter();
    return !!(f.from || f.to || f.categoryId || f.technicianId);
  });

  protected readonly categories = toSignal(
    inject(TicketsService)
      .categories()
      .pipe(catchError(() => of([] as Category[]))),
    { initialValue: [] as Category[] },
  );
  /** Technician filter (admins only: the API gives a technician their own figures anyway). */
  protected readonly technicians = toSignal(
    this.auth.role() === 'ADMIN'
      ? inject(UsersService)
          .list({ role: 'TECHNICIAN' }, 0, 100, 'lastName,asc')
          .pipe(
            map((page) => page.content),
            catchError(() => of([] as User[])),
          )
      : of([] as User[]),
    { initialValue: [] as User[] },
  );

  private readonly reload = signal(0);
  protected readonly state = loadState(
    computed(() => ({ f: this.filter(), attempt: this.reload() })),
    ({ f }) => this.api.summary(f),
  );
  protected readonly report = computed(() => dataOf(this.state()));

  protected readonly periodError = signal<string | null>(null);
  protected readonly exporting = signal<ExportFormat | null>(null);

  protected readonly byPriority = computed(() => bars(this.report()?.byPriority ?? []));
  protected readonly byCategory = computed(() => bars(this.report()?.byCategory ?? []));
  protected readonly ratings = computed(() =>
    (this.report()?.satisfaction.distribution ?? [])
      .map((count, index) => ({
        label: `${index + 1} étoile${index > 0 ? 's' : ''}`,
        value: count,
      }))
      .reverse(),
  );

  /** Dates shown in the inputs: the URL's, or the period the API chose by default. */
  protected readonly from = computed(() => this.filter().from ?? this.report()?.scope.from ?? '');
  protected readonly to = computed(() => this.filter().to ?? this.report()?.scope.to ?? '');

  protected setPeriod(from: string, to: string): void {
    const error = reportPeriodError(from || null, to || null);
    this.periodError.set(error);
    if (!error) {
      this.update({ from: from || null, to: to || null });
    }
  }

  protected update(change: Params): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: change,
      queryParamsHandling: 'merge',
    });
  }

  protected reset(): void {
    this.periodError.set(null);
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  protected download(format: ExportFormat): void {
    this.exporting.set(format);
    this.api.export(this.filter(), format).subscribe({
      next: (file) => {
        this.exporting.set(null);
        this.save(file);
      },
      error: (error: unknown) => {
        this.exporting.set(null);
        this.toasts.error(toApiProblem(error).detail);
      },
    });
  }

  private save(file: ExportedFile): void {
    const url = URL.createObjectURL(file.blob);
    const link = this.document.createElement('a');
    link.href = url;
    link.download = file.fileName;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }

  protected day(value: string): string {
    return value.split('-').reverse().join('/');
  }

  protected exact(at: string): string {
    return formatDateTime(at);
  }
}
