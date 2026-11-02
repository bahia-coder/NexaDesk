import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  CircleCheck,
  Clock,
  LucideAngularModule,
  RefreshCw,
  Star,
  Ticket,
  TriangleAlert,
} from 'lucide-angular';

import { AuthService } from '../../core/auth/auth.service';
import { BarListComponent, BarListItem } from '../../shared/ui/bar-list.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { StatCardComponent } from '../../shared/ui/stat-card.component';
import { AvatarComponent } from '../../shared/ui/avatar.component';
import { ActivityFeedComponent } from './activity-feed.component';
import {
  changePercent,
  formatHours,
  formatMinutes,
  formatPercent,
  formatRating,
} from './dashboard.format';
import { CountItem, PERIODS, PeriodDays } from './dashboard.models';
import { DashboardService } from './dashboard.service';
import { dataOf, loadState } from './load-state';
import { TimelineChartComponent } from './timeline-chart.component';

const PERIOD_LABELS: Record<PeriodDays, string> = {
  7: '7 jours',
  30: '30 jours',
  90: '90 jours',
  365: '12 mois',
};

function bars(items: CountItem[]): BarListItem[] {
  return items.map((item) => ({ label: item.label, value: item.count, color: item.color }));
}

/** Overview for admins: activity of the period, current queue, SLA, satisfaction, technicians. */
@Component({
  selector: 'nx-admin-dashboard',
  imports: [
    LucideAngularModule,
    StatCardComponent,
    BarListComponent,
    SkeletonComponent,
    EmptyStateComponent,
    AvatarComponent,
    TimelineChartComponent,
    ActivityFeedComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './dashboard.scss',
})
export class AdminDashboardComponent {
  private readonly service = inject(DashboardService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  /** Period from the URL (?days=), so a view can be shared and survives a reload. */
  readonly days = input<string>();

  protected readonly periods = PERIODS;
  protected readonly periodLabels = PERIOD_LABELS;
  protected readonly icons = { CircleCheck, Clock, RefreshCw, Star, Ticket, TriangleAlert };
  protected readonly format = { formatHours, formatMinutes, formatPercent, formatRating };

  protected readonly period = computed<PeriodDays>(() => {
    const days = Number(this.days());
    return (PERIODS as readonly number[]).includes(days) ? (days as PeriodDays) : 30;
  });

  private readonly reload = signal(0);
  protected readonly state = loadState(
    computed(() => ({ days: this.period(), attempt: this.reload() })),
    ({ days }) => this.service.admin(days),
  );
  protected readonly data = computed(() => dataOf(this.state()));

  protected readonly avgChange = computed(() => {
    const kpis = this.data()?.kpis;
    return kpis ? changePercent(kpis.avgResolutionHours, kpis.avgResolutionHoursPrevious) : null;
  });

  protected readonly byPriority = computed(() => bars(this.data()?.byPriority ?? []));
  protected readonly byCategory = computed(() => bars(this.data()?.byCategory ?? []));
  protected readonly ratings = computed(() =>
    (this.data()?.satisfaction.distribution ?? [])
      .map((count, index) => ({
        label: `${index + 1} étoile${index > 0 ? 's' : ''}`,
        value: count,
      }))
      .reverse(),
  );

  protected setPeriod(days: PeriodDays): void {
    void this.router.navigate([], { queryParams: { days }, queryParamsHandling: 'merge' });
  }

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }
}
