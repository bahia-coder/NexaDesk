import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Clock, LucideAngularModule, RefreshCw, TriangleAlert, Wrench } from 'lucide-angular';
import { map } from 'rxjs';

import { formatDateTime } from '../../core/format/time';
import { dataOf, loadState } from '../../core/http/load-state';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PaginatorComponent } from '../../shared/ui/paginator.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { StatCardComponent } from '../../shared/ui/stat-card.component';
import { formatMinutes } from '../dashboard/dashboard.format';
import { InterventionsService } from './interventions.service';

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const PAGE_SIZE = 20;

/** A technician's own work log over a period, with the time spent (default: the last 30 days, set by the API). */
@Component({
  selector: 'nx-my-interventions-page',
  imports: [
    RouterLink,
    LucideAngularModule,
    EmptyStateComponent,
    PaginatorComponent,
    SkeletonComponent,
    StatCardComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page__header">
        <div>
          <h1 class="page-title">Mes interventions</h1>
          <p class="muted">Votre journal de travail sur les tickets.</p>
        </div>
        <form class="period" (submit)="$event.preventDefault()">
          <label class="field">
            <span class="field__label">Du</span>
            <input
              class="input"
              type="date"
              [value]="query().from ?? ''"
              (change)="setPeriod($any($event.target).value, query().to)"
            />
          </label>
          <label class="field">
            <span class="field__label">Au</span>
            <input
              class="input"
              type="date"
              [value]="query().to ?? ''"
              (change)="setPeriod(query().from, $any($event.target).value)"
            />
          </label>
        </form>
      </header>

      @if (state().status === 'error') {
        <div class="card">
          <nx-empty-state
            title="Journal indisponible"
            description="Vérifiez la période choisie (la date de début doit précéder la date de fin)."
            [icon]="icons.TriangleAlert"
          >
            <button type="button" class="btn btn--primary" (click)="retry()">
              <lucide-icon [img]="icons.RefreshCw" [size]="16" aria-hidden="true" /> Réessayer
            </button>
          </nx-empty-state>
        </div>
      } @else {
        @let d = data();
        <section class="kpis" aria-label="Synthèse de la période">
          <nx-stat-card
            label="Temps saisi sur la période"
            [value]="minutes(d?.totalMinutes ?? 0)"
            [icon]="icons.Clock"
            tone="indigo"
            [hint]="query().from || query().to ? 'Période choisie' : '30 derniers jours'"
            [loading]="!d"
          />
          <nx-stat-card
            label="Interventions"
            [value]="d?.page?.totalElements ?? 0"
            [icon]="icons.Wrench"
            tone="violet"
            [loading]="!d"
          />
        </section>

        <section class="card list">
          @if (d) {
            @if (d.page.content.length === 0) {
              <nx-empty-state
                title="Aucune intervention sur la période"
                description="Les interventions se saisissent depuis l’onglet « Interventions » d’un ticket en cours."
                [icon]="icons.Wrench"
              >
                <a
                  class="btn btn--secondary"
                  routerLink="/tickets"
                  [queryParams]="{ scope: 'ASSIGNED_TO_ME' }"
                >
                  Voir mes tickets
                </a>
              </nx-empty-state>
            } @else {
              <div class="table-wrap">
                <table class="table">
                  <thead>
                    <tr>
                      <th scope="col">Date</th>
                      <th scope="col">Ticket</th>
                      <th scope="col">Diagnostic</th>
                      <th scope="col" class="num">Temps</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (i of d.page.content; track i.id) {
                      <tr>
                        <td class="nowrap">{{ exact(i.createdAt) }}</td>
                        <td>
                          <a class="mono" [routerLink]="['/tickets', i.ticketId]">{{
                            i.ticketReference
                          }}</a>
                          <span class="muted small block">{{ i.ticketTitle }}</span>
                        </td>
                        <td class="text">{{ i.diagnosis }}</td>
                        <td class="num nowrap">{{ minutes(i.timeSpentMinutes) }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
              <nx-paginator
                [page]="d.page.page"
                [size]="d.page.size"
                [total]="d.page.totalElements"
                itemLabel="intervention"
                itemsLabel="interventions"
                (pageChange)="goTo($event)"
              />
            }
          } @else {
            <nx-skeleton height="40px" [lines]="6" />
          }
        </section>
      }
    </div>
  `,
  styles: `
    .page {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
      max-width: 1200px;
      margin: 0 auto;
      padding: var(--space-6);

      @media (width < 640px) {
        padding: var(--space-4);
      }
    }

    .page__header {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      justify-content: space-between;
      gap: var(--space-4);
    }

    .period {
      display: flex;
      gap: var(--space-3);
    }

    .kpis {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: var(--space-4);

      @media (width < 640px) {
        grid-template-columns: 1fr;
      }
    }

    .list {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
    }

    .table-wrap {
      overflow-x: auto;
    }

    .table {
      width: 100%;
      border-collapse: collapse;
      font: var(--text-small);

      th,
      td {
        padding: var(--space-3);
        border-bottom: 1px solid var(--color-border);
        text-align: left;
        vertical-align: top;
      }

      thead th {
        font: var(--text-xs);
        letter-spacing: 0.04em;
        text-transform: uppercase;
        color: var(--color-text-muted);
      }

      .num {
        text-align: right;
      }
    }

    .text {
      max-width: 520px;
      overflow-wrap: anywhere;
    }

    .nowrap {
      white-space: nowrap;
    }

    .block {
      display: block;
    }

    .small {
      font: var(--text-xs);
    }
  `,
})
export class MyInterventionsPage {
  private readonly api = inject(InterventionsService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly icons = { Clock, RefreshCw, TriangleAlert, Wrench };

  /** Period and page from the URL (?from=yyyy-MM-dd&to=…&page=), so the view survives a reload. */
  protected readonly query = toSignal(
    this.route.queryParams.pipe(
      map((p) => ({
        from: ISO_DATE.test(p['from'] ?? '') ? (p['from'] as string) : null,
        to: ISO_DATE.test(p['to'] ?? '') ? (p['to'] as string) : null,
        page: Math.max(0, Number(p['page']) || 0),
      })),
    ),
    { initialValue: { from: null as string | null, to: null as string | null, page: 0 } },
  );

  private readonly reload = signal(0);
  protected readonly state = loadState(
    computed(() => ({ q: this.query(), attempt: this.reload() })),
    ({ q }) => this.api.mine(q.from, q.to, q.page, PAGE_SIZE),
  );
  protected readonly data = computed(() => dataOf(this.state()));

  protected setPeriod(from: string | null, to: string | null): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { from: from || null, to: to || null, page: null },
    });
  }

  protected goTo(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: page || null },
      queryParamsHandling: 'merge',
    });
  }

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }

  protected minutes(value: number): string {
    return formatMinutes(value);
  }

  protected exact(at: string): string {
    return formatDateTime(at);
  }
}
