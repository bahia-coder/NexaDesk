import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Lock, LucideAngularModule, Send } from 'lucide-angular';

import { formatDateTime, formatRelative } from '../../../core/format/time';
import { toApiProblem } from '../../../core/http/api-error';
import { ClockService } from '../../../core/services/clock.service';
import { AvatarComponent } from '../../../shared/ui/avatar.component';
import { SpinnerComponent } from '../../../shared/ui/spinner.component';
import { Comment, TicketDetail } from '../tickets.models';
import { TicketsService } from '../tickets.service';

/** Messages of a ticket; internal notes (staff only) are marked and shaded. */
@Component({
  selector: 'nx-ticket-conversation',
  imports: [FormsModule, LucideAngularModule, AvatarComponent, SpinnerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="messages">
      @for (c of comments(); track c.id) {
        <li class="message" [class.message--internal]="c.internal">
          <nx-avatar [name]="c.author.fullName" [color]="c.author.avatarColor" [size]="32" />
          <div class="message__body">
            <p class="message__head">
              <strong>{{ c.author.fullName }}</strong>
              @if (c.author.id === ticket().requester.id) {
                <span class="muted">· demandeur</span>
              }
              @if (c.internal) {
                <span class="badge badge--warning">
                  <lucide-icon [img]="icons.Lock" [size]="12" aria-hidden="true" /> Note interne
                </span>
              }
              <time class="muted" [attr.datetime]="c.createdAt" [attr.title]="exact(c.createdAt)">
                {{ relative(c.createdAt) }}
              </time>
            </p>
            <p class="message__text">{{ c.body }}</p>
          </div>
        </li>
      } @empty {
        <li class="muted empty">Aucun message pour l’instant.</li>
      }
    </ol>

    @if (canComment()) {
      <form class="composer" (ngSubmit)="send()">
        <label class="sr-only" for="reply">Votre message</label>
        <textarea
          id="reply"
          class="textarea"
          name="body"
          [(ngModel)]="body"
          maxlength="5000"
          [placeholder]="internal ? 'Note visible par le support uniquement…' : 'Répondre…'"
        ></textarea>
        @if (error(); as message) {
          <p class="field__error" role="alert">{{ message }}</p>
        }
        <div class="composer__actions">
          @if (canCommentInternally()) {
            <div class="segmented" role="group" aria-label="Visibilité du message">
              <button
                type="button"
                class="segmented__item"
                [attr.aria-pressed]="!internal"
                (click)="internal = false"
              >
                Public
              </button>
              <button
                type="button"
                class="segmented__item"
                [attr.aria-pressed]="internal"
                (click)="internal = true"
              >
                <lucide-icon [img]="icons.Lock" [size]="12" aria-hidden="true" /> Interne
              </button>
            </div>
          }
          <button type="submit" class="btn btn--primary" [disabled]="sending() || !body.trim()">
            @if (sending()) {
              <nx-spinner />
            } @else {
              <lucide-icon [img]="icons.Send" [size]="16" aria-hidden="true" />
            }
            Envoyer
          </button>
        </div>
      </form>
    }
  `,
  styleUrl: './ticket-detail.scss',
})
export class TicketConversationComponent {
  private readonly api = inject(TicketsService);
  private readonly clock = inject(ClockService);

  readonly ticket = input.required<TicketDetail>();
  readonly comments = input.required<Comment[]>();
  /** A message can change the ticket (first response time, resuming a ticket waiting for the requester). */
  readonly posted = output<Comment>();

  protected readonly icons = { Lock, Send };
  protected body = '';
  protected internal = false;
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly canComment = computed(() =>
    this.ticket().availableActions.includes('COMMENT'),
  );
  protected readonly canCommentInternally = computed(() =>
    this.ticket().availableActions.includes('COMMENT_INTERNAL'),
  );

  protected send(): void {
    const body = this.body.trim();
    if (!body || this.sending()) {
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    this.api
      .addComment(this.ticket().id, body, this.internal && this.canCommentInternally())
      .subscribe({
        next: (comment) => {
          this.body = '';
          this.sending.set(false);
          this.posted.emit(comment);
        },
        error: (error: unknown) => {
          this.sending.set(false);
          this.error.set(toApiProblem(error).detail);
        },
      });
  }

  protected relative(at: string): string {
    return formatRelative(at, this.clock.now());
  }

  protected exact(at: string): string {
    return formatDateTime(at);
  }
}
