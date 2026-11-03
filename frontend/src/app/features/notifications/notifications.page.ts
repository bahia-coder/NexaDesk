import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  linkedSignal,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import {
  BellOff,
  Check,
  CheckCheck,
  LucideAngularModule,
  RefreshCw,
  TriangleAlert,
} from 'lucide-angular';
import { map } from 'rxjs';

import { safeReturnUrl } from '../../core/auth/auth.guards';
import { LoadState, dataOf, loadState } from '../../core/http/load-state';
import { Page } from '../../core/models/page.models';
import { AppNotification } from '../../core/notifications/notification.models';
import { NotificationService } from '../../core/notifications/notification.service';
import { ToastService } from '../../core/services/toast.service';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PaginatorComponent } from '../../shared/ui/paginator.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { SpinnerComponent } from '../../shared/ui/spinner.component';
import { NotificationItemComponent } from './notification-item.component';

const PAGE_SIZE = 20;

/** Every notification of the signed-in user, most recent first; filter and page in the URL (?unread=1&page=). */
@Component({
  selector: 'nx-notifications-page',
  imports: [
    LucideAngularModule,
    EmptyStateComponent,
    PaginatorComponent,
    SkeletonComponent,
    SpinnerComponent,
    NotificationItemComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <header class="page__header">
        <div>
          <h1 class="page-title">Notifications</h1>
          <p class="muted">
            Affectations, réponses, changements de statut et alertes SLA sur vos tickets.
          </p>
        </div>
        <button
          type="button"
          class="btn btn--secondary"
          [disabled]="notifications.unread() === 0 || markingAll()"
          (click)="readAll()"
        >
          @if (markingAll()) {
            <nx-spinner [size]="16" />
          } @else {
            <lucide-icon [img]="icons.CheckCheck" [size]="16" aria-hidden="true" />
          }
          Tout marquer comme lu
        </button>
      </header>

      <div class="tabs" role="tablist" aria-label="Filtre">
        <button
          type="button"
          role="tab"
          class="tab"
          [attr.aria-selected]="!query().unread"
          (click)="filter(false)"
        >
          Toutes
        </button>
        <button
          type="button"
          role="tab"
          class="tab"
          [attr.aria-selected]="query().unread"
          (click)="filter(true)"
        >
          Non lues
          @if (notifications.unread() > 0) {
            <span class="badge badge--info">{{ notifications.unread() }}</span>
          }
        </button>
      </div>

      <section class="card list" role="tabpanel">
        @if (page(); as p) {
          @if (p.content.length === 0) {
            <nx-empty-state
              [title]="query().unread ? 'Tout est lu' : 'Aucune notification'"
              [description]="
                query().unread
                  ? 'Vous avez pris connaissance de toutes vos notifications.'
                  : 'Vous serez prévenu ici dès qu’un ticket qui vous concerne évolue.'
              "
              [icon]="icons.BellOff"
            />
          } @else {
            <ul class="items">
              @for (n of p.content; track n.id) {
                <li class="row" [class.row--unread]="!n.read">
                  <button type="button" class="row__open" (click)="open(n)">
                    <nx-notification-item [notification]="n" />
                  </button>
                  @if (!n.read) {
                    <button
                      type="button"
                      class="btn btn--ghost btn--icon btn--sm"
                      aria-label="Marquer comme lue"
                      title="Marquer comme lue"
                      [disabled]="marking() === n.id"
                      (click)="markRead(n)"
                    >
                      <lucide-icon [img]="icons.Check" [size]="16" aria-hidden="true" />
                    </button>
                  }
                </li>
              }
            </ul>
            <nx-paginator
              [page]="p.page"
              [size]="p.size"
              [total]="p.totalElements"
              itemLabel="notification"
              itemsLabel="notifications"
              (pageChange)="goTo($event)"
            />
          }
        } @else if (state().status === 'error') {
          <nx-empty-state
            title="Notifications indisponibles"
            description="Le serveur ne répond pas pour le moment."
            [icon]="icons.TriangleAlert"
          >
            <button type="button" class="btn btn--primary" (click)="retry()">
              <lucide-icon [img]="icons.RefreshCw" [size]="16" aria-hidden="true" /> Réessayer
            </button>
          </nx-empty-state>
        } @else {
          <nx-skeleton height="56px" [lines]="6" />
        }
      </section>
    </div>
  `,
  styles: `
    .page {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
      max-width: 880px;
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

    .tabs {
      display: flex;
      gap: var(--space-1);
      border-bottom: 1px solid var(--color-border);
    }

    .tab {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
      height: 40px;
      padding: 0 var(--space-3);
      border: 0;
      border-bottom: 2px solid transparent;
      background: none;
      color: var(--color-text-muted);
      font: 500 0.875rem/1 var(--font-sans);
      cursor: pointer;

      &[aria-selected='true'] {
        border-bottom-color: var(--color-primary);
        color: var(--color-text);
      }
    }

    .list {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
    }

    .items {
      display: flex;
      flex-direction: column;
    }

    .row {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      padding-right: var(--space-2);
      border-bottom: 1px solid var(--color-border);
      border-radius: var(--radius-sm);
    }

    .row--unread {
      background: var(--color-primary-subtle);
    }

    .row__open {
      display: flex;
      flex: 1;
      min-width: 0;
      padding: var(--space-3);
      border: 0;
      border-radius: var(--radius-sm);
      background: transparent;
      cursor: pointer;

      &:hover {
        background: var(--color-surface-hover);
      }
    }
  `,
})
export class NotificationsPage {
  protected readonly notifications = inject(NotificationService);
  private readonly toasts = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly icons = { BellOff, Check, CheckCheck, RefreshCw, TriangleAlert };

  protected readonly query = toSignal(
    this.route.queryParams.pipe(
      map((p) => ({ unread: p['unread'] === '1', page: Math.max(0, Number(p['page']) || 0) })),
    ),
    { initialValue: { unread: false, page: 0 } },
  );

  private readonly reload = signal(0);
  protected readonly state = loadState(
    computed(() => ({
      q: this.query(),
      attempt: this.reload(),
      arrivals: this.notifications.arrivals(),
    })),
    ({ q }) => this.notifications.list(q.unread, q.page, PAGE_SIZE),
  );
  /** Current page, updated in place when an item is read; kept while a refresh is in flight. */
  protected readonly page = linkedSignal<
    LoadState<Page<AppNotification>>,
    Page<AppNotification> | null
  >({
    source: this.state,
    computation: (state, previous) => dataOf(state) ?? previous?.value ?? null,
  });

  protected readonly marking = signal<number | null>(null);
  protected readonly markingAll = signal(false);

  protected open(notification: AppNotification): void {
    if (!notification.read) {
      this.notifications.markRead(notification).subscribe();
    }
    if (notification.link) {
      void this.router.navigateByUrl(safeReturnUrl(notification.link));
    }
  }

  protected markRead(notification: AppNotification): void {
    this.marking.set(notification.id);
    this.notifications.markRead(notification).subscribe({
      next: (updated) => {
        this.marking.set(null);
        this.page.update((p) =>
          p ? { ...p, content: p.content.map((n) => (n.id === updated.id ? updated : n)) } : p,
        );
      },
      error: () => this.marking.set(null),
    });
  }

  protected readAll(): void {
    this.markingAll.set(true);
    this.notifications.markAllRead().subscribe({
      next: (updated) => {
        this.markingAll.set(false);
        this.reload.update((n) => n + 1);
        this.toasts.success(
          updated > 1
            ? `${updated} notifications marquées comme lues.`
            : 'Notification marquée comme lue.',
        );
      },
      error: () => this.markingAll.set(false),
    });
  }

  protected filter(unread: boolean): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { unread: unread ? 1 : null, page: null },
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
}
