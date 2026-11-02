import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Star } from 'lucide-angular';

import { toApiProblem } from '../../../core/http/api-error';
import { ToastService } from '../../../core/services/toast.service';
import { SpinnerComponent } from '../../../shared/ui/spinner.component';
import { TicketDetail } from '../tickets.models';
import { TicketsService } from '../tickets.service';

/** The requester's rating of a solved ticket (once), or the rating already given. */
@Component({
  selector: 'nx-ticket-feedback',
  imports: [FormsModule, LucideAngularModule, SpinnerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (ticket().feedback; as f) {
      <p class="stars" [attr.aria-label]="f.rating + ' sur 5'">
        @for (n of [1, 2, 3, 4, 5]; track n) {
          <lucide-icon
            [img]="icons.Star"
            [size]="18"
            [class.star--on]="n <= f.rating"
            aria-hidden="true"
          />
        }
      </p>
      @if (f.comment) {
        <p class="small">« {{ f.comment }} »</p>
      }
    } @else if (ticket().availableActions.includes('GIVE_FEEDBACK')) {
      <form (ngSubmit)="send()" class="feedback">
        <fieldset class="stars-input">
          <legend class="field__label">Votre satisfaction</legend>
          @for (n of [1, 2, 3, 4, 5]; track n) {
            <label [attr.title]="n + ' sur 5'">
              <input class="sr-only" type="radio" name="rating" [value]="n" [(ngModel)]="rating" />
              <lucide-icon
                [img]="icons.Star"
                [size]="22"
                [class.star--on]="n <= rating"
                aria-hidden="true"
              />
              <span class="sr-only">{{ n }} sur 5</span>
            </label>
          }
        </fieldset>
        <textarea
          class="textarea"
          name="comment"
          [(ngModel)]="comment"
          maxlength="1000"
          placeholder="Un commentaire (facultatif)"
        ></textarea>
        <button
          type="submit"
          class="btn btn--primary btn--sm"
          [disabled]="rating === 0 || sending()"
        >
          @if (sending()) {
            <nx-spinner />
          }
          Envoyer mon avis
        </button>
      </form>
    } @else {
      <p class="muted small">Pas encore d’évaluation.</p>
    }
  `,
  styleUrl: './ticket-detail.scss',
})
export class TicketFeedbackComponent {
  private readonly api = inject(TicketsService);
  private readonly toasts = inject(ToastService);

  readonly ticket = input.required<TicketDetail>();
  readonly sent = output<void>();

  protected readonly icons = { Star };
  protected rating = 0;
  protected comment = '';
  protected readonly sending = signal(false);

  protected send(): void {
    if (this.rating === 0) {
      return;
    }
    this.sending.set(true);
    this.api.giveFeedback(this.ticket().id, this.rating, this.comment.trim() || null).subscribe({
      next: () => {
        this.sending.set(false);
        this.toasts.success('Merci pour votre avis !');
        this.sent.emit();
      },
      error: (error: unknown) => {
        this.sending.set(false);
        this.toasts.error(toApiProblem(error).detail);
      },
    });
  }
}
