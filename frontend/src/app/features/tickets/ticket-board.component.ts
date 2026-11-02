import {
  CdkDrag,
  CdkDragDrop,
  CdkDragStart,
  CdkDropList,
  CdkDropListGroup,
  transferArrayItem,
} from '@angular/cdk/drag-drop';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, RefreshCw, TriangleAlert } from 'lucide-angular';
import { defaultIfEmpty } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { STATUS_LABELS } from '../../core/format/labels';
import { dataOf, loadState } from '../../core/http/load-state';
import { TicketStatus, TicketSummary } from '../../core/models/ticket.models';
import { AvatarComponent } from '../../shared/ui/avatar.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PriorityBadgeComponent } from '../../shared/ui/priority-badge.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { SlaIndicatorComponent } from '../../shared/ui/sla-indicator.component';
import { BOARD_COLUMNS, moveAction } from './board-moves';
import { TicketActionsService } from './ticket-actions.service';
import { TicketFilter } from './tickets.models';
import { TicketsService } from './tickets.service';

type Columns = Record<TicketStatus, TicketSummary[]>;

/**
 * Kanban of open and resolved tickets. Dragging a card runs the matching workflow action; the card moves at once
 * (optimistic update) and goes back if the action is cancelled or refused by the API.
 */
@Component({
  selector: 'nx-ticket-board',
  imports: [
    CdkDropListGroup,
    CdkDropList,
    CdkDrag,
    RouterLink,
    LucideAngularModule,
    AvatarComponent,
    EmptyStateComponent,
    PriorityBadgeComponent,
    SkeletonComponent,
    SlaIndicatorComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (state().status === 'error') {
      <div class="card">
        <nx-empty-state
          title="Kanban indisponible"
          description="Les tickets n’ont pas pu être chargés."
          [icon]="icons.TriangleAlert"
        >
          <button type="button" class="btn btn--primary" (click)="retry()">
            <lucide-icon [img]="icons.RefreshCw" [size]="16" aria-hidden="true" /> Réessayer
          </button>
        </nx-empty-state>
      </div>
    } @else {
      <p class="hint muted">
        Glissez une carte pour faire avancer le ticket. Les colonnes grisées ne sont pas une étape
        possible.
      </p>
      <div class="board" cdkDropListGroup>
        @for (status of columnsOrder; track status) {
          <section
            class="column"
            [class.column--disabled]="dragging() !== null && !accepts(dragging()!, status)"
            [attr.aria-label]="labels[status]"
          >
            <header class="column__head">
              <span>{{ labels[status] }}</span>
              <span class="column__count">{{ columns()?.[status]?.length ?? '' }}</span>
            </header>
            @if (columns(); as cols) {
              <div
                class="column__cards"
                cdkDropList
                [id]="status"
                [cdkDropListData]="cols[status]"
                [cdkDropListEnterPredicate]="enterPredicate(status)"
                (cdkDropListDropped)="drop($event, status)"
              >
                @for (ticket of cols[status]; track ticket.id) {
                  <article
                    class="card-ticket"
                    cdkDrag
                    [cdkDragData]="ticket"
                    (cdkDragStarted)="started($event)"
                    (cdkDragEnded)="dragging.set(null)"
                  >
                    <a class="mono ref" [routerLink]="['/tickets', ticket.id]">{{
                      ticket.reference
                    }}</a>
                    <p class="title">{{ ticket.title }}</p>
                    <div class="meta">
                      <nx-priority-badge
                        [code]="ticket.priority.code"
                        [label]="ticket.priority.label"
                      />
                      @if (ticket.assignee; as a) {
                        <nx-avatar [name]="a.fullName" [color]="a.avatarColor" [size]="22" />
                      }
                    </div>
                    @if (status !== 'RESOLVED') {
                      <nx-sla-indicator [sla]="ticket.sla" />
                    }
                  </article>
                } @empty {
                  <p class="empty muted">Aucun ticket</p>
                }
              </div>
            } @else {
              <nx-skeleton height="96px" [lines]="3" />
            }
          </section>
        }
      </div>
    }
  `,
  styles: `
    .hint {
      font: var(--text-small);
    }

    .board {
      display: grid;
      grid-template-columns: repeat(5, minmax(240px, 1fr));
      gap: var(--space-3);
      margin-top: var(--space-2);
      overflow-x: auto;
      padding-bottom: var(--space-2);
    }

    .column {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      min-height: 320px;
      padding: var(--space-3);
      border-radius: var(--radius-md);
      background: var(--color-surface-hover);
      transition: opacity var(--duration-fast) var(--ease-standard);
    }

    .column--disabled {
      opacity: 0.4;
    }

    .column__head {
      display: flex;
      justify-content: space-between;
      font: var(--text-xs);
      letter-spacing: 0.04em;
      text-transform: uppercase;
      color: var(--color-text-muted);
    }

    .column__count {
      font-weight: 600;
    }

    .column__cards {
      display: flex;
      flex: 1;
      flex-direction: column;
      gap: var(--space-2);
      min-height: 80px;
    }

    .card-ticket {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      padding: var(--space-3);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      background: var(--color-surface);
      cursor: grab;

      &:hover {
        box-shadow: var(--shadow-sm);
      }
    }

    .ref {
      font-size: 0.75rem;
    }

    .title {
      font: 500 0.8125rem/1.125rem var(--font-sans);
    }

    .meta {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .empty {
      padding: var(--space-4) 0;
      text-align: center;
      font: var(--text-small);
    }

    .cdk-drag-preview {
      box-shadow: var(--shadow-lg);
    }

    .cdk-drag-placeholder {
      opacity: 0.35;
    }
  `,
})
export class TicketBoardComponent {
  private readonly api = inject(TicketsService);
  private readonly actions = inject(TicketActionsService);
  private readonly auth = inject(AuthService);

  readonly filter = input.required<TicketFilter>();

  protected readonly icons = { RefreshCw, TriangleAlert };
  protected readonly labels = STATUS_LABELS;
  protected readonly columnsOrder = BOARD_COLUMNS;

  private readonly reload = signal(0);
  protected readonly state = loadState(
    computed(() => ({ filter: this.filter(), attempt: this.reload() })),
    ({ filter }) => this.api.board(filter),
  );
  /** Local copy, moved optimistically and reset by every new load. */
  protected readonly columns = linkedSignal<Columns | null>(() => {
    const data = dataOf(this.state());
    if (!data) {
      return null;
    }
    const columns = {} as Columns;
    BOARD_COLUMNS.forEach((status) => (columns[status] = [...(data[status] ?? [])]));
    return columns;
  });

  /** Column the dragged card comes from. */
  protected readonly dragging = signal<TicketStatus | null>(null);

  protected accepts(from: TicketStatus, to: TicketStatus): boolean {
    return from === to || moveAction(from, to, this.auth.role()) !== null;
  }

  protected enterPredicate(to: TicketStatus) {
    return (drag: CdkDrag<TicketSummary>) => this.accepts(drag.data.status, to);
  }

  protected started(event: CdkDragStart<TicketSummary>): void {
    this.dragging.set(event.source.data.status);
  }

  protected drop(event: CdkDragDrop<TicketSummary[]>, to: TicketStatus): void {
    if (event.previousContainer === event.container) {
      return;
    }
    const ticket = event.item.data as TicketSummary;
    const from = ticket.status;
    const action = moveAction(from, to, this.auth.role());
    if (!action) {
      return;
    }
    transferArrayItem(
      event.previousContainer.data,
      event.container.data,
      event.previousIndex,
      event.currentIndex,
    );
    this.columns.update((cols) => (cols ? { ...cols } : cols));

    this.actions
      .run(action, {
        id: ticket.id,
        reference: ticket.reference,
        categoryId: ticket.category.id,
        subcategoryId: null,
        priority: ticket.priority.code,
      })
      .pipe(defaultIfEmpty(null))
      .subscribe((updated) => {
        if (updated) {
          // The card takes the server's version (status, assignee, priority, SLA).
          this.columns.update((cols) =>
            cols
              ? {
                  ...cols,
                  [to]: cols[to].map((t) =>
                    t.id === ticket.id
                      ? {
                          ...t,
                          status: updated.status,
                          assignee: updated.assignee,
                          priority: updated.priority,
                          sla: updated.sla,
                          updatedAt: updated.updatedAt,
                        }
                      : t,
                  ),
                }
              : cols,
          );
        } else {
          this.rollback(ticket, from, to);
        }
      });
  }

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }

  private rollback(ticket: TicketSummary, from: TicketStatus, to: TicketStatus): void {
    this.columns.update((cols) => {
      if (!cols) {
        return cols;
      }
      return {
        ...cols,
        [to]: cols[to].filter((t) => t.id !== ticket.id),
        [from]: [ticket, ...cols[from].filter((t) => t.id !== ticket.id)],
      };
    });
  }
}
