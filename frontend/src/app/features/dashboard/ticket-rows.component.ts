import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { TicketSummary } from '../../core/models/ticket.models';
import { PriorityBadgeComponent } from '../../shared/ui/priority-badge.component';
import { SlaIndicatorComponent } from '../../shared/ui/sla-indicator.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';

/** Compact ticket list for the dashboards: reference, title, priority, status and SLA. */
@Component({
  selector: 'nx-ticket-rows',
  imports: [RouterLink, PriorityBadgeComponent, StatusBadgeComponent, SlaIndicatorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (tickets().length === 0) {
      <p class="muted empty">{{ emptyText() }}</p>
    } @else {
      <ul class="rows">
        @for (ticket of tickets(); track ticket.id) {
          <li class="row">
            <div class="row__main">
              <span class="mono muted">{{ ticket.reference }}</span>
              <a
                class="row__title"
                [routerLink]="['/tickets', ticket.id]"
                [attr.title]="ticket.title"
              >
                {{ ticket.title }}
              </a>
            </div>
            <div class="row__meta">
              <nx-priority-badge [code]="ticket.priority.code" [label]="ticket.priority.label" />
              <nx-status-badge [status]="ticket.status" />
              @if (showSla()) {
                <nx-sla-indicator [sla]="ticket.sla" />
              }
            </div>
          </li>
        }
      </ul>
    }
  `,
  styles: `
    .rows {
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-2) var(--space-4);
      padding: var(--space-3) 0;
      border-bottom: 1px solid var(--color-border);

      &:last-child {
        border-bottom: 0;
      }
    }

    .row__main {
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-width: 0;
      flex: 1 1 220px;
    }

    .row__title {
      color: var(--color-text);
      font-weight: 500;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .row__meta {
      display: flex;
      align-items: center;
      gap: var(--space-2);
    }

    .empty {
      padding: var(--space-6) 0;
      text-align: center;
    }
  `,
})
export class TicketRowsComponent {
  readonly tickets = input.required<TicketSummary[]>();
  readonly emptyText = input('Aucun ticket.');
  readonly showSla = input(true);
}
