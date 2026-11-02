import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  CircleCheck,
  Flame,
  LucideAngularModule,
  RefreshCw,
  Ticket,
  TriangleAlert,
} from 'lucide-angular';

import { AuthService } from '../../core/auth/auth.service';
import { BarListComponent } from '../../shared/ui/bar-list.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { StatCardComponent } from '../../shared/ui/stat-card.component';
import { ActivityFeedComponent } from './activity-feed.component';
import { formatHours, formatMinutes, formatPercent } from './dashboard.format';
import { DashboardService } from './dashboard.service';
import { dataOf, loadState } from '../../core/http/load-state';
import { TicketRowsComponent } from './ticket-rows.component';

/** A technician's day: own queue sorted by SLA deadline, urgent and overdue tickets, own figures. */
@Component({
  selector: 'nx-technician-dashboard',
  imports: [
    LucideAngularModule,
    StatCardComponent,
    BarListComponent,
    SkeletonComponent,
    EmptyStateComponent,
    ActivityFeedComponent,
    TicketRowsComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page__header">
        <div>
          <h1 class="page-title">Bonjour {{ auth.user()?.firstName }}</h1>
          <p class="muted">Vos tickets et vos chiffres de la semaine.</p>
        </div>
      </header>

      @if (state().status === 'error') {
        <div class="card">
          <nx-empty-state
            title="Tableau de bord indisponible"
            description="Les indicateurs n’ont pas pu être chargés."
            [icon]="icons.TriangleAlert"
          >
            <button type="button" class="btn btn--primary" (click)="retry()">
              <lucide-icon [img]="icons.RefreshCw" [size]="16" aria-hidden="true" />
              Réessayer
            </button>
          </nx-empty-state>
        </div>
      } @else {
        @let d = data();
        <section class="kpis" aria-label="Indicateurs clés">
          <nx-stat-card
            label="Mes tickets ouverts"
            [value]="d?.assignedOpen ?? 0"
            [icon]="icons.Ticket"
            tone="indigo"
            [hint]="(d?.pending ?? 0) + ' en attente'"
            [loading]="!d"
          />
          <nx-stat-card
            label="Urgents"
            [value]="d?.urgent ?? 0"
            [icon]="icons.Flame"
            tone="orange"
            hint="Priorité haute, critique ou SLA menacé"
            [loading]="!d"
          />
          <nx-stat-card
            label="En retard (SLA)"
            [value]="d?.overdue ?? 0"
            [icon]="icons.TriangleAlert"
            tone="danger"
            [hint]="(d?.atRisk ?? 0) + ' à risque d’ici l’échéance'"
            [loading]="!d"
          />
          <nx-stat-card
            label="Résolus cette semaine"
            [value]="d?.resolvedThisWeek ?? 0"
            [icon]="icons.CircleCheck"
            tone="success"
            [loading]="!d"
          />
        </section>

        <section class="queue card" aria-label="Mes chiffres">
          @if (d) {
            <div class="queue__item">
              <span class="queue__value">{{ d.unassignedInSpecialties }}</span>
              <span class="muted">Non assignés dans mes spécialités</span>
            </div>
            <div class="queue__item">
              <span class="queue__value">{{ format.formatMinutes(d.minutesThisWeek) }}</span>
              <span class="muted">Temps saisi cette semaine</span>
            </div>
            <div class="queue__item">
              <span class="queue__value">{{ format.formatHours(d.avgResolutionHours) }}</span>
              <span class="muted">Temps moyen de résolution (30 j)</span>
            </div>
            <div class="queue__item">
              <span class="queue__value">{{ format.formatPercent(d.slaRate) }}</span>
              <span class="muted">SLA de résolution respectés (30 j)</span>
            </div>
          } @else {
            <nx-skeleton height="40px" />
          }
        </section>

        <div class="grid grid--wide">
          <section class="card panel" aria-labelledby="queue-title">
            <div class="panel__head">
              <h2 id="queue-title" class="card__title">À traiter maintenant</h2>
              <span class="muted small">Par échéance SLA</span>
            </div>
            @if (d) {
              <nx-ticket-rows
                [tickets]="d.queue"
                emptyText="Aucun ticket en cours : votre file est vide."
              />
            } @else {
              <nx-skeleton height="40px" [lines]="5" />
            }
          </section>
          <section class="card panel" aria-labelledby="category-title">
            <h2 id="category-title" class="card__title">Mes tickets des 30 derniers jours</h2>
            @if (d) {
              <nx-bar-list
                [items]="byCategory()"
                label="Mes tickets des 30 derniers jours, par catégorie"
                emptyText="Aucun ticket sur les 30 derniers jours."
                [hideZero]="true"
              />
            } @else {
              <nx-skeleton height="18px" [lines]="4" />
            }
          </section>
        </div>

        <section class="card panel" aria-labelledby="activity-title">
          <h2 id="activity-title" class="card__title">Activité récente sur mes tickets</h2>
          @if (d) {
            <nx-activity-feed [items]="d.recentActivity" />
          } @else {
            <nx-skeleton height="16px" [lines]="5" />
          }
        </section>
      }
    </div>
  `,
  styleUrl: './dashboard.scss',
})
export class TechnicianDashboardComponent {
  private readonly service = inject(DashboardService);
  protected readonly auth = inject(AuthService);

  protected readonly icons = { CircleCheck, Flame, RefreshCw, Ticket, TriangleAlert };
  protected readonly format = { formatHours, formatMinutes, formatPercent };

  private readonly reload = signal(0);
  protected readonly state = loadState(this.reload, () => this.service.technician());
  protected readonly data = computed(() => dataOf(this.state()));
  protected readonly byCategory = computed(() =>
    (this.data()?.byCategory ?? []).map((item) => ({ label: item.label, value: item.count })),
  );

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }
}
