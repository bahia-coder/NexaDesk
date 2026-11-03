import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';

import {
  AppNotification,
  NOTIFICATION_VISUALS,
} from '../../core/notifications/notification.models';
import { formatDateTime, formatRelative } from '../../core/format/time';
import { ClockService } from '../../core/services/clock.service';

/** One notification: type icon, title, message, age and an unread dot. Wrapped by a button in lists. */
@Component({
  selector: 'nx-notification-item',
  imports: [LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let n = notification();
    <span [class]="'icon badge badge--' + visual().tone">
      <lucide-icon [img]="visual().icon" [size]="16" aria-hidden="true" />
    </span>
    <span class="body">
      <span class="title" [class.title--unread]="!n.read">{{ n.title }}</span>
      <span class="message">{{ n.message }}</span>
      <time class="time" [attr.datetime]="n.createdAt" [attr.title]="exact()">{{
        relative()
      }}</time>
    </span>
    @if (!n.read) {
      <span class="dot" aria-hidden="true"></span>
      <span class="sr-only">Non lue</span>
    }
  `,
  styles: `
    :host {
      display: flex;
      align-items: flex-start;
      gap: var(--space-3);
      width: 100%;
      min-width: 0;
      text-align: left;
    }

    .icon {
      flex: none;
      justify-content: center;
      width: 32px;
      height: 32px;
      padding: 0;
      border-radius: var(--radius-full);
    }

    .body {
      display: flex;
      flex: 1;
      flex-direction: column;
      gap: 2px;
      min-width: 0;
    }

    .title {
      font: var(--text-small);
      color: var(--color-text);
    }

    .title--unread {
      font-weight: 600;
    }

    .message {
      display: -webkit-box;
      overflow: hidden;
      -webkit-box-orient: vertical;
      -webkit-line-clamp: 2;
      font: var(--text-small);
      color: var(--color-text-muted);
      overflow-wrap: anywhere;
    }

    .time {
      font: var(--text-xs);
      color: var(--color-text-muted);
    }

    .dot {
      flex: none;
      width: 8px;
      height: 8px;
      margin-top: 6px;
      border-radius: var(--radius-full);
      background: var(--color-primary);
    }
  `,
})
export class NotificationItemComponent {
  private readonly clock = inject(ClockService);

  readonly notification = input.required<AppNotification>();

  protected readonly visual = computed(() => NOTIFICATION_VISUALS[this.notification().type]);
  protected readonly relative = computed(() =>
    formatRelative(this.notification().createdAt, this.clock.now()),
  );
  protected readonly exact = computed(() => formatDateTime(this.notification().createdAt));
}
