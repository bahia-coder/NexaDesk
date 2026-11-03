import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ArrowLeft, LucideAngularModule, Pencil, RefreshCw, TriangleAlert } from 'lucide-angular';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, filter, of } from 'rxjs';

import { formatDateTime, formatRelative } from '../../../core/format/time';
import { toApiProblem } from '../../../core/http/api-error';
import { dataOf, loadState } from '../../../core/http/load-state';
import { ClockService } from '../../../core/services/clock.service';
import { ToastService } from '../../../core/services/toast.service';
import { AvatarComponent } from '../../../shared/ui/avatar.component';
import { EmptyStateComponent } from '../../../shared/ui/empty-state.component';
import { PriorityBadgeComponent } from '../../../shared/ui/priority-badge.component';
import { SkeletonComponent } from '../../../shared/ui/skeleton.component';
import { SlaIndicatorComponent } from '../../../shared/ui/sla-indicator.component';
import { SpinnerComponent } from '../../../shared/ui/spinner.component';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge.component';
import { ACTION_LABELS, TicketActionsService, WORKFLOW_ACTIONS } from '../ticket-actions.service';
import { IMPACT_LABELS, PENDING_LABELS, TYPE_LABELS, URGENCY_LABELS } from '../tickets.labels';
import { Attachment, Comment, HistoryEntry, TicketAction, TicketDetail } from '../tickets.models';
import { TicketsService } from '../tickets.service';
import { TicketAttachmentsComponent } from './ticket-attachments.component';
import { TicketConversationComponent } from './ticket-conversation.component';
import { TicketFeedbackComponent } from './ticket-feedback.component';
import { TicketHistoryComponent } from './ticket-history.component';
import { TicketInterventionsComponent } from '../../interventions/ticket-interventions.component';
import { TicketKnowledgeComponent } from '../../knowledge/ticket-knowledge.component';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificationService } from '../../../core/notifications/notification.service';

type Tab = 'conversation' | 'interventions' | 'attachments' | 'history';

/** Main action first (filled button), the others secondary; destructive ones last. */
const PRIMARY: TicketAction[] = ['TAKE', 'START', 'RESUME', 'RESOLVE', 'CONFIRM_RESOLUTION'];

@Component({
  selector: 'nx-ticket-detail-page',
  imports: [
    FormsModule,
    RouterLink,
    LucideAngularModule,
    AvatarComponent,
    EmptyStateComponent,
    PriorityBadgeComponent,
    SkeletonComponent,
    SlaIndicatorComponent,
    SpinnerComponent,
    StatusBadgeComponent,
    TicketAttachmentsComponent,
    TicketConversationComponent,
    TicketFeedbackComponent,
    TicketHistoryComponent,
    TicketInterventionsComponent,
    TicketKnowledgeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './ticket-detail.page.html',
  styleUrl: './ticket-detail.scss',
})
export class TicketDetailPage {
  private readonly api = inject(TicketsService);
  private readonly actions = inject(TicketActionsService);
  private readonly toasts = inject(ToastService);
  private readonly clock = inject(ClockService);
  private readonly auth = inject(AuthService);

  /** The work log is for support staff (the API refuses it to employees). */
  protected readonly staff = computed(() => this.auth.role() !== 'EMPLOYEE');

  /** Route parameter (component input binding). */
  readonly id = input.required<string>();

  protected readonly icons = { ArrowLeft, Pencil, RefreshCw, TriangleAlert };
  protected readonly labels = {
    type: TYPE_LABELS,
    impact: IMPACT_LABELS,
    urgency: URGENCY_LABELS,
    pending: PENDING_LABELS,
    action: ACTION_LABELS,
  };

  private readonly ticketId = computed(() => Number(this.id()));
  private readonly reload = signal(0);
  protected readonly state = loadState(
    computed(() => ({ id: this.ticketId(), attempt: this.reload() })),
    ({ id }) => this.api.get(id),
  );
  /** Replaced in place by each action's response (no reload, no flicker). */
  protected readonly ticket = linkedSignal<TicketDetail | null>(() => dataOf(this.state()));

  private readonly related = signal(0);
  protected readonly comments = loadState(
    computed(() => ({ id: this.ticketId(), version: this.related() })),
    ({ id }) => this.api.comments(id).pipe(catchError(() => of([] as Comment[]))),
  );
  protected readonly attachments = loadState(
    computed(() => ({ id: this.ticketId(), version: this.related() })),
    ({ id }) => this.api.attachments(id).pipe(catchError(() => of([] as Attachment[]))),
  );
  protected readonly history = loadState(
    computed(() => ({ id: this.ticketId(), version: this.related() })),
    ({ id }) => this.api.history(id).pipe(catchError(() => of([] as HistoryEntry[]))),
  );

  constructor() {
    // Someone else acted on this ticket (reply, status, assignment): shown without a manual reload.
    inject(NotificationService)
      .received$.pipe(
        filter((notification) => notification.ticketId === this.ticketId()),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        this.related.update((n) => n + 1);
        this.api.get(this.ticketId()).subscribe((updated) => this.ticket.set(updated));
      });
  }

  protected readonly tab = signal<Tab>('conversation');
  protected readonly running = signal<TicketAction | null>(null);

  protected readonly workflowActions = computed(() => {
    const available = this.ticket()?.availableActions ?? [];
    return WORKFLOW_ACTIONS.filter((action) => available.includes(action)).sort(
      (a, b) => Number(PRIMARY.includes(b)) - Number(PRIMARY.includes(a)),
    );
  });

  // ---- Inline edit of title and description (EDIT) ----------------------------------------------------
  protected readonly editing = signal(false);
  protected readonly saving = signal(false);
  protected editTitle = '';
  protected editDescription = '';
  protected readonly editError = signal<string | null>(null);

  protected run(action: TicketAction): void {
    const t = this.ticket();
    if (!t || this.running()) {
      return;
    }
    this.running.set(action);
    this.actions
      .run(action, {
        id: t.id,
        reference: t.reference,
        categoryId: t.category.id,
        subcategoryId: t.subcategory?.id ?? null,
        priority: t.priority.code,
      })
      .subscribe({
        next: (updated) => {
          this.ticket.set(updated);
          this.related.update((n) => n + 1);
        },
        complete: () => this.running.set(null),
      });
  }

  protected startEdit(t: TicketDetail): void {
    this.editTitle = t.title;
    this.editDescription = t.description;
    this.editError.set(null);
    this.editing.set(true);
  }

  protected saveEdit(t: TicketDetail): void {
    const title = this.editTitle.trim();
    const description = this.editDescription.trim();
    if (title.length < 5 || description.length < 10) {
      this.editError.set('Le titre fait au moins 5 caractères et la description au moins 10.');
      return;
    }
    this.saving.set(true);
    this.api.update(t.id, title, description, t.equipment?.id ?? null, t.version).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.editing.set(false);
        this.ticket.set(updated);
        this.related.update((n) => n + 1);
        this.toasts.success('Ticket modifié.');
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.editError.set(toApiProblem(error).detail);
      },
    });
  }

  /** A comment can resume a ticket or record the first response: refresh the ticket too. */
  protected onPosted(): void {
    this.related.update((n) => n + 1);
    this.api.get(this.ticketId()).subscribe((updated) => this.ticket.set(updated));
  }

  /** New work: history entries, and possibly the solution offered when resolving. */
  protected onInterventionsChanged(): void {
    this.related.update((n) => n + 1);
  }

  protected onAttachmentsChanged(): void {
    this.related.update((n) => n + 1);
  }

  protected onFeedback(): void {
    this.related.update((n) => n + 1);
    this.api.get(this.ticketId()).subscribe((updated) => this.ticket.set(updated));
  }

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }

  protected relative(at: string): string {
    return formatRelative(at, this.clock.now());
  }

  protected exact(at: string | null): string {
    return at ? formatDateTime(at) : '—';
  }

  protected dataOf = dataOf;
}
