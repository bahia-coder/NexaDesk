import { Dialog } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { LucideAngularModule, Pencil, Plus } from 'lucide-angular';

import { formatDateTime, formatRelative } from '../../core/format/time';
import { dataOf, loadState } from '../../core/http/load-state';
import { ClockService } from '../../core/services/clock.service';
import { ToastService } from '../../core/services/toast.service';
import { AvatarComponent } from '../../shared/ui/avatar.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { formatMinutes } from '../dashboard/dashboard.format';
import { TicketDetail } from '../tickets/tickets.models';
import {
  InterventionDialogComponent,
  InterventionDialogData,
} from './intervention-dialog.component';
import { Intervention } from './interventions.models';
import { InterventionsService } from './interventions.service';

/** Work log of a ticket (support staff): who did what, how long; add while the ticket is being worked on. */
@Component({
  selector: 'nx-ticket-interventions',
  imports: [LucideAngularModule, AvatarComponent, SkeletonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (data(); as d) {
      <div class="summary">
        <span class="muted">
          {{ d.interventions.length }} intervention{{ d.interventions.length > 1 ? 's' : '' }} ·
          {{ minutes(d.totalMinutes) }} au total
        </span>
        @if (canLog()) {
          <button type="button" class="btn btn--primary btn--sm" (click)="open()">
            <lucide-icon [img]="icons.Plus" [size]="16" aria-hidden="true" />
            Ajouter une intervention
          </button>
        }
      </div>
      <ol class="interventions">
        @for (i of d.interventions; track i.id) {
          <li class="intervention">
            <nx-avatar
              [name]="i.technician.fullName"
              [color]="i.technician.avatarColor"
              [size]="32"
            />
            <div class="intervention__body">
              <p class="intervention__head">
                <strong>{{ i.technician.fullName }}</strong>
                <span class="badge badge--indigo">{{ minutes(i.timeSpentMinutes) }}</span>
                <time class="muted" [attr.datetime]="i.createdAt" [attr.title]="exact(i.createdAt)">
                  {{ relative(i.createdAt) }}
                </time>
                @if (i.editable) {
                  <button
                    type="button"
                    class="btn btn--ghost btn--icon btn--sm edit"
                    aria-label="Modifier l’intervention"
                    (click)="open(i)"
                  >
                    <lucide-icon [img]="icons.Pencil" [size]="14" aria-hidden="true" />
                  </button>
                }
              </p>
              <p class="label">Diagnostic</p>
              <p class="text">{{ i.diagnosis }}</p>
              @if (i.solution) {
                <p class="label">Solution</p>
                <p class="text">{{ i.solution }}</p>
              }
            </div>
          </li>
        } @empty {
          <li class="muted empty">
            Aucune intervention.
            @if (!canLog()) {
              Elles se saisissent quand le ticket est en cours ou en attente.
            }
          </li>
        }
      </ol>
    } @else if (state().status === 'error') {
      <p class="muted empty">Les interventions n’ont pas pu être chargées.</p>
    } @else {
      <nx-skeleton height="56px" [lines]="2" />
    }
  `,
  styles: `
    .summary {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-3);
      margin-bottom: var(--space-4);
      font: var(--text-small);
    }

    .interventions {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .intervention {
      display: flex;
      gap: var(--space-3);
    }

    .intervention__body {
      flex: 1;
      min-width: 0;
      padding: var(--space-3);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
    }

    .intervention__head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--space-2);
      margin-bottom: var(--space-2);
      font: var(--text-small);
    }

    .edit {
      margin-left: auto;
    }

    .label {
      margin-top: var(--space-2);
      font: var(--text-xs);
      letter-spacing: 0.04em;
      text-transform: uppercase;
      color: var(--color-text-muted);
    }

    .text {
      white-space: pre-wrap;
      overflow-wrap: anywhere;
    }

    .empty {
      padding: var(--space-4) 0;
    }
  `,
})
export class TicketInterventionsComponent {
  private readonly api = inject(InterventionsService);
  private readonly dialog = inject(Dialog);
  private readonly toasts = inject(ToastService);
  private readonly clock = inject(ClockService);

  readonly ticket = input.required<TicketDetail>();
  /** Logging work adds history entries and may provide the resolution text. */
  readonly changed = output<void>();

  protected readonly icons = { Pencil, Plus };

  private readonly version = signal(0);
  protected readonly state = loadState(
    computed(() => ({ id: this.ticket().id, version: this.version() })),
    ({ id }) => this.api.forTicket(id),
  );
  protected readonly data = computed(() => dataOf(this.state()));
  protected readonly canLog = computed(() =>
    this.ticket().availableActions.includes('LOG_INTERVENTION'),
  );

  protected open(intervention?: Intervention): void {
    const ticket = this.ticket();
    this.dialog
      .open<Intervention, InterventionDialogData>(InterventionDialogComponent, {
        data: { ticketId: ticket.id, reference: ticket.reference, intervention },
        ariaLabelledBy: 'nx-intervention-title',
        panelClass: 'nx-dialog-panel',
        backdropClass: 'nx-backdrop',
      })
      .closed.subscribe((saved) => {
        if (saved) {
          this.toasts.success(
            intervention ? 'Intervention modifiée.' : 'Intervention enregistrée.',
          );
          this.version.update((n) => n + 1);
          this.changed.emit();
        }
      });
  }

  protected minutes(value: number): string {
    return formatMinutes(value);
  }

  protected relative(at: string): string {
    return formatRelative(at, this.clock.now());
  }

  protected exact(at: string): string {
    return formatDateTime(at);
  }
}
