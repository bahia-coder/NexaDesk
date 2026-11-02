import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';

import { formatDateTime, formatRelative } from '../../../core/format/time';
import { ClockService } from '../../../core/services/clock.service';
import { describeActivity } from '../../dashboard/dashboard.format';
import { HistoryEntry } from '../tickets.models';

/** Business timeline of a ticket, oldest first, with the reason given for each step. */
@Component({
  selector: 'nx-ticket-history',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="timeline">
      @for (h of history(); track h.id) {
        <li class="timeline__item">
          <span class="timeline__dot" aria-hidden="true"></span>
          <div>
            <p>{{ describe(h) }}</p>
            @if (h.comment) {
              <p class="muted small">« {{ h.comment }} »</p>
            }
            <time
              class="muted small"
              [attr.datetime]="h.createdAt"
              [attr.title]="exact(h.createdAt)"
            >
              {{ relative(h.createdAt) }}
            </time>
          </div>
        </li>
      }
    </ol>
  `,
  styleUrl: './ticket-detail.scss',
})
export class TicketHistoryComponent {
  private readonly clock = inject(ClockService);

  readonly history = input.required<HistoryEntry[]>();

  protected describe(h: HistoryEntry): string {
    return describeActivity({
      at: h.createdAt,
      action: h.action,
      ticketId: 0,
      ticketReference: '',
      actorName: h.actor?.fullName ?? null,
      field: h.field,
      oldValue: h.oldValue,
      newValue: h.newValue,
      comment: h.comment,
    });
  }

  protected relative(at: string): string {
    return formatRelative(at, this.clock.now());
  }

  protected exact(at: string): string {
    return formatDateTime(at);
  }
}
