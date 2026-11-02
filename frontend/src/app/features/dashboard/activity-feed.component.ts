import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';

import { formatDateTime, formatRelative } from '../../core/format/time';
import { ClockService } from '../../core/services/clock.service';
import { describeActivity } from './dashboard.format';
import { ActivityItem } from './dashboard.models';

/** Latest ticket events, newest first, with the relative time (exact date in the tooltip). */
@Component({
  selector: 'nx-activity-feed',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (items().length === 0) {
      <p class="muted empty">Aucune activité récente.</p>
    } @else {
      <ol class="feed">
        @for (item of items(); track $index) {
          <li class="feed__item">
            <span class="feed__dot" aria-hidden="true"></span>
            <div class="feed__body">
              <p>
                <span class="mono">{{ item.ticketReference }}</span>
                · {{ describe(item) }}
              </p>
              <time class="feed__time" [attr.datetime]="item.at" [attr.title]="exact(item.at)">
                {{ relative(item.at) }}
              </time>
            </div>
          </li>
        }
      </ol>
    }
  `,
  styles: `
    .feed {
      display: flex;
      flex-direction: column;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .feed__item {
      position: relative;
      display: flex;
      gap: var(--space-3);
      padding-bottom: var(--space-4);

      &:not(:last-child)::before {
        content: '';
        position: absolute;
        top: 14px;
        bottom: 0;
        left: 3px;
        width: 1px;
        background: var(--color-border);
      }
    }

    .feed__dot {
      flex-shrink: 0;
      width: 7px;
      height: 7px;
      margin-top: 6px;
      border-radius: var(--radius-full);
      background: var(--color-primary);
    }

    .feed__body {
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-width: 0;
      font: var(--text-small);
    }

    .feed__time {
      color: var(--color-text-muted);
      font: var(--text-xs);
    }

    .empty {
      padding: var(--space-6) 0;
      text-align: center;
    }
  `,
})
export class ActivityFeedComponent {
  private readonly clock = inject(ClockService);

  readonly items = input.required<ActivityItem[]>();

  protected describe(item: ActivityItem): string {
    return describeActivity(item);
  }

  protected relative(at: string): string {
    return formatRelative(at, this.clock.now());
  }

  protected exact(at: string): string {
    return formatDateTime(at);
  }
}
