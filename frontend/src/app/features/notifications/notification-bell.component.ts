import { CdkMenuTrigger } from '@angular/cdk/menu';
import { ConnectedPosition } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Bell, LucideAngularModule } from 'lucide-angular';

import { NotificationService } from '../../core/notifications/notification.service';
import { NotificationPanelComponent } from './notification-panel.component';

/** Header bell: unread count (kept live over WebSocket) and the latest notifications on click. */
@Component({
  selector: 'nx-notification-bell',
  imports: [CdkMenuTrigger, LucideAngularModule, NotificationPanelComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      class="btn btn--ghost btn--icon bell"
      [cdkMenuTriggerFor]="panel"
      [cdkMenuPosition]="position"
      [attr.aria-label]="label()"
    >
      <lucide-icon [img]="icons.Bell" [size]="18" aria-hidden="true" />
      @if (badge(); as text) {
        <span class="bell__count" aria-hidden="true">{{ text }}</span>
      }
    </button>
    <ng-template #panel><nx-notification-panel /></ng-template>
  `,
  styles: `
    .bell {
      position: relative;
    }

    .bell__count {
      position: absolute;
      top: 2px;
      right: 2px;
      min-width: 18px;
      height: 18px;
      padding: 0 5px;
      border: 2px solid var(--color-surface);
      border-radius: var(--radius-full);
      background: var(--color-danger-solid);
      color: #fff;
      font: 600 0.6875rem/14px var(--font-sans);
      text-align: center;
    }
  `,
})
export class NotificationBellComponent {
  private readonly notifications = inject(NotificationService);

  protected readonly icons = { Bell };
  /** Right-aligned under the bell, so the panel stays in the window. */
  protected readonly position: ConnectedPosition[] = [
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top' },
  ];

  protected readonly badge = computed(() => {
    const count = this.notifications.unread();
    return count === 0 ? null : count > 99 ? '99+' : String(count);
  });
  protected readonly label = computed(() => {
    const count = this.notifications.unread();
    return count === 0 ? 'Notifications' : `Notifications, ${count} non lue${count > 1 ? 's' : ''}`;
  });
}
