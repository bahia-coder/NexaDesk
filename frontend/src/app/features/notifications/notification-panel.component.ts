import { CdkMenu, CdkMenuItem } from '@angular/cdk/menu';
import { ChangeDetectionStrategy, Component, inject, linkedSignal } from '@angular/core';
import { Router } from '@angular/router';
import { map } from 'rxjs';

import { safeReturnUrl } from '../../core/auth/auth.guards';
import { LoadState, dataOf, loadState } from '../../core/http/load-state';
import { AppNotification } from '../../core/notifications/notification.models';
import { NotificationService } from '../../core/notifications/notification.service';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { NotificationItemComponent } from './notification-item.component';

const LATEST = 6;

/**
 * Content of the header bell: the latest notifications. Created each time the menu opens, so it is always
 * fresh; reloads when a notification arrives while it is open.
 */
@Component({
  selector: 'nx-notification-panel',
  imports: [CdkMenu, CdkMenuItem, SkeletonComponent, NotificationItemComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="panel" cdkMenu aria-label="Dernières notifications">
      <div class="panel__head">
        <span class="panel__title">Notifications</span>
        <button
          type="button"
          class="panel__link"
          cdkMenuItem
          [cdkMenuItemDisabled]="notifications.unread() === 0"
          (cdkMenuItemTriggered)="readAll()"
        >
          Tout marquer comme lu
        </button>
      </div>

      @if (items(); as list) {
        @if (list.length === 0) {
          <p class="panel__state">Aucune notification pour l’instant.</p>
        } @else {
          @for (n of list; track n.id) {
            <button
              type="button"
              class="panel__item"
              [class.panel__item--unread]="!n.read"
              cdkMenuItem
              (cdkMenuItemTriggered)="open(n)"
            >
              <nx-notification-item [notification]="n" />
            </button>
          }
        }
      } @else if (state().status === 'error') {
        <p class="panel__state">Notifications indisponibles pour le moment.</p>
      } @else {
        <div class="panel__state"><nx-skeleton height="44px" [lines]="3" /></div>
      }

      <button type="button" class="panel__all" cdkMenuItem (cdkMenuItemTriggered)="all()">
        Voir toutes les notifications
      </button>
    </div>
  `,
  styles: `
    .panel {
      display: flex;
      flex-direction: column;
      width: min(380px, calc(100vw - 32px));
      max-height: min(560px, calc(100vh - 80px));
      margin-top: var(--space-1);
      overflow-y: auto;
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      background: var(--color-surface-raised);
      box-shadow: var(--shadow-md);
    }

    .panel__head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-3);
      padding: var(--space-3) var(--space-4);
      border-bottom: 1px solid var(--color-border);
    }

    .panel__title {
      font-weight: 600;
    }

    .panel__link {
      border: 0;
      background: none;
      color: var(--color-primary);
      font: var(--text-small);
      cursor: pointer;

      &:hover:not([aria-disabled='true']),
      &:focus-visible {
        text-decoration: underline;
        outline: none;
      }

      &[aria-disabled='true'] {
        color: var(--color-text-muted);
        cursor: default;
      }
    }

    .panel__item {
      display: flex;
      padding: var(--space-3) var(--space-4);
      border: 0;
      border-bottom: 1px solid var(--color-border);
      background: transparent;
      cursor: pointer;

      &:hover,
      &:focus-visible {
        background: var(--color-surface-hover);
        outline: none;
      }
    }

    .panel__item--unread {
      background: var(--color-primary-subtle);
    }

    .panel__state {
      padding: var(--space-6) var(--space-4);
      color: var(--color-text-muted);
      text-align: center;
    }

    .panel__all {
      padding: var(--space-3) var(--space-4);
      border: 0;
      background: transparent;
      color: var(--color-primary);
      font: 500 0.875rem/1.2 var(--font-sans);
      cursor: pointer;

      &:hover,
      &:focus-visible {
        background: var(--color-surface-hover);
        outline: none;
      }
    }
  `,
})
export class NotificationPanelComponent {
  protected readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);

  protected readonly state = loadState(this.notifications.arrivals, () =>
    this.notifications.list(false, 0, LATEST).pipe(map((page) => page.content)),
  );
  /** Keeps the current list while a refresh is in flight. */
  protected readonly items = linkedSignal<LoadState<AppNotification[]>, AppNotification[] | null>({
    source: this.state,
    computation: (state, previous) => dataOf(state) ?? previous?.value ?? null,
  });

  protected open(notification: AppNotification): void {
    if (!notification.read) {
      this.notifications.markRead(notification).subscribe();
    }
    if (notification.link) {
      void this.router.navigateByUrl(safeReturnUrl(notification.link));
    }
  }

  protected readAll(): void {
    this.notifications.markAllRead().subscribe();
  }

  protected all(): void {
    void this.router.navigateByUrl('/notifications');
  }
}
