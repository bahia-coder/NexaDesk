import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  CircleCheck,
  MessageSquarePlus,
  Hourglass,
  LucideAngularModule,
  RefreshCw,
  ThumbsUp,
  TriangleAlert,
  Wrench,
} from 'lucide-angular';

import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { StatCardComponent } from '../../shared/ui/stat-card.component';
import { DashboardService } from './dashboard.service';
import { dataOf, loadState } from '../../core/http/load-state';
import { TicketRowsComponent } from './ticket-rows.component';

/** An employee's requests: where each one stands, and the resolutions waiting for their confirmation. */
@Component({
  selector: 'nx-employee-dashboard',
  imports: [
    RouterLink,
    LucideAngularModule,
    StatCardComponent,
    SkeletonComponent,
    EmptyStateComponent,
    TicketRowsComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page__header">
        <div>
          <h1 class="page-title">Bonjour {{ auth.user()?.firstName }}</h1>
          <p class="muted">Où en sont vos demandes au support informatique.</p>
        </div>
      </header>

      <a class="cta card" routerLink="/tickets/new">
        <span class="cta__icon"
          ><lucide-icon [img]="icons.MessageSquarePlus" [size]="22" aria-hidden="true"
        /></span>
        <span>
          <strong>Un problème ? Décrivez-le</strong>
          <span class="muted">Le support vous répond, et vous suivez votre demande ici.</span>
        </span>
      </a>

      @if (state().status === 'error') {
        <div class="card">
          <nx-empty-state
            title="Tableau de bord indisponible"
            description="Vos demandes n’ont pas pu être chargées."
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
        <section class="kpis" aria-label="Mes demandes">
          <nx-stat-card
            label="En attente de prise en charge"
            [value]="d?.waiting ?? 0"
            [icon]="icons.Hourglass"
            tone="info"
            [loading]="!d"
          />
          <nx-stat-card
            label="En cours de traitement"
            [value]="d?.inProgress ?? 0"
            [icon]="icons.Wrench"
            tone="violet"
            [loading]="!d"
          />
          <nx-stat-card
            label="À confirmer"
            [value]="d?.toConfirm ?? 0"
            [icon]="icons.ThumbsUp"
            tone="warning"
            hint="Résolus : votre avis est attendu"
            [loading]="!d"
          />
          <nx-stat-card
            label="Résolus"
            [value]="d?.resolved ?? 0"
            [icon]="icons.CircleCheck"
            tone="success"
            [loading]="!d"
          />
        </section>

        @if (d && d.awaitingConfirmation.length > 0) {
          <section class="card panel confirm" aria-labelledby="confirm-title">
            <div class="panel__head">
              <h2 id="confirm-title" class="card__title">
                Résolus, en attente de votre confirmation
              </h2>
            </div>
            <p class="muted small">
              Le support a résolu ces demandes. Sans réponse de votre part, elles seront fermées
              automatiquement après 72 heures ouvrées.
            </p>
            <nx-ticket-rows [tickets]="d.awaitingConfirmation" [showSla]="false" />
          </section>
        }

        <section class="card panel" aria-labelledby="recent-title">
          <h2 id="recent-title" class="card__title">Mes dernières demandes</h2>
          @if (d) {
            <nx-ticket-rows
              [tickets]="d.recent"
              [showSla]="false"
              emptyText="Vous n’avez encore fait aucune demande."
            />
          } @else {
            <nx-skeleton height="40px" [lines]="4" />
          }
        </section>
      }
    </div>
  `,
  styleUrl: './dashboard.scss',
  styles: `
    .cta {
      display: flex;
      align-items: center;
      gap: var(--space-4);
      color: var(--color-text);
      transition: border-color var(--duration-fast) var(--ease-standard);

      &:hover {
        border-color: var(--color-primary);
        text-decoration: none;
      }

      > span:last-child {
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
    }

    .cta__icon {
      display: grid;
      place-items: center;
      width: 44px;
      height: 44px;
      border-radius: var(--radius-md);
      background: var(--color-primary-subtle);
      color: var(--color-primary);
    }

    .confirm {
      border-color: var(--color-warning);
      box-shadow: inset 3px 0 0 var(--color-warning);
    }
  `,
})
export class EmployeeDashboardComponent {
  private readonly service = inject(DashboardService);
  protected readonly auth = inject(AuthService);

  protected readonly icons = {
    CircleCheck,
    MessageSquarePlus,
    Hourglass,
    RefreshCw,
    ThumbsUp,
    TriangleAlert,
    Wrench,
  };

  private readonly reload = signal(0);
  protected readonly state = loadState(this.reload, () => this.service.employee());
  protected readonly data = computed(() => dataOf(this.state()));

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }
}
