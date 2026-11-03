import { Dialog } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { BookOpen, FilePen, Link2, LucideAngularModule, X } from 'lucide-angular';
import { catchError, of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { toApiProblem } from '../../core/http/api-error';
import { dataOf, loadState } from '../../core/http/load-state';
import { ToastService } from '../../core/services/toast.service';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { SpinnerComponent } from '../../shared/ui/spinner.component';
import { TicketDetail } from '../tickets/tickets.models';
import { ARTICLE_STATUS_LABELS, ArticleSummary } from './knowledge.models';
import { KnowledgeService } from './knowledge.service';
import { LinkArticleDialogComponent } from './link-article-dialog.component';

/**
 * Articles linked to a ticket. The ticket's technician or an admin links and unlinks published articles, and
 * turns a resolved ticket into a draft (same rules as the API). The requester only sees published articles.
 */
@Component({
  selector: 'nx-ticket-knowledge',
  imports: [RouterLink, LucideAngularModule, SkeletonComponent, SpinnerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (articles(); as list) {
      @if (list.length || manage()) {
        <section class="card facts">
          <h2 class="card__title">Base de connaissances</h2>
          @if (list.length) {
            <ul class="links">
              @for (a of list; track a.id) {
                <li>
                  <lucide-icon [img]="icons.BookOpen" [size]="14" aria-hidden="true" />
                  <a [routerLink]="['/knowledge', a.id]">{{ a.title }}</a>
                  @if (a.status !== 'PUBLISHED') {
                    <span class="badge badge--warning">{{ statusLabels[a.status] }}</span>
                  }
                  @if (manage()) {
                    <button
                      type="button"
                      class="btn btn--ghost btn--icon btn--sm"
                      [attr.aria-label]="'Délier « ' + a.title + ' »'"
                      [disabled]="busy()"
                      (click)="unlink(a)"
                    >
                      <lucide-icon [img]="icons.X" [size]="14" aria-hidden="true" />
                    </button>
                  }
                </li>
              }
            </ul>
          } @else {
            <p class="muted small">Aucun article lié.</p>
          }
          @if (manage()) {
            <div class="buttons">
              <button
                type="button"
                class="btn btn--secondary btn--sm"
                [disabled]="busy()"
                (click)="link(list)"
              >
                <lucide-icon [img]="icons.Link2" [size]="14" aria-hidden="true" /> Lier un article
              </button>
              @if (canDraft()) {
                <button
                  type="button"
                  class="btn btn--secondary btn--sm"
                  [disabled]="busy()"
                  (click)="draft()"
                >
                  @if (drafting()) {
                    <nx-spinner [size]="14" />
                  } @else {
                    <lucide-icon [img]="icons.FilePen" [size]="14" aria-hidden="true" />
                  }
                  Rédiger un article
                </button>
              }
            </div>
          }
        </section>
      }
    } @else if (state().status === 'loading') {
      <nx-skeleton height="80px" />
    }
  `,
  styles: `
    .links {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      margin: 0;
      padding: 0;
      list-style: none;
      font: var(--text-small);

      li {
        display: flex;
        align-items: center;
        gap: var(--space-2);

        a {
          flex: 1;
          min-width: 0;
          overflow-wrap: anywhere;
        }

        lucide-icon {
          flex: none;
          color: var(--color-primary);
        }
      }
    }

    .buttons {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
    }

    .small {
      font: var(--text-xs);
    }
  `,
})
export class TicketKnowledgeComponent {
  private readonly api = inject(KnowledgeService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(Dialog);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);

  readonly ticket = input.required<TicketDetail>();

  protected readonly icons = { BookOpen, FilePen, Link2, X };
  protected readonly statusLabels = ARTICLE_STATUS_LABELS;

  protected readonly state = loadState(
    computed(() => this.ticket().id),
    (id) => this.api.ticketArticles(id).pipe(catchError(() => of([] as ArticleSummary[]))),
  );
  /** Replaced by each change's response. */
  protected readonly articles = linkedSignal<ArticleSummary[] | null>(() => dataOf(this.state()));

  /** The ticket's technician or an admin (enforced by the API too). */
  protected readonly manage = computed(() => {
    const user = this.auth.user();
    const t = this.ticket();
    return user?.role === 'ADMIN' || (user?.role === 'TECHNICIAN' && t.assignee?.id === user.id);
  });
  /** A resolved ticket with its solution can become a draft. */
  protected readonly canDraft = computed(() => {
    const t = this.ticket();
    return (t.status === 'RESOLVED' || t.status === 'CLOSED') && !!t.resolution?.trim();
  });

  protected readonly busy = signal(false);
  protected readonly drafting = signal(false);

  protected link(current: ArticleSummary[]): void {
    this.dialog
      .open<ArticleSummary, { linkedIds: number[] }>(LinkArticleDialogComponent, {
        data: { linkedIds: current.map((a) => a.id) },
        ariaLabelledBy: 'nx-link-article-title',
        panelClass: 'nx-dialog-panel',
        backdropClass: 'nx-backdrop',
      })
      .closed.subscribe((chosen) => {
        if (chosen) {
          this.save([...current.map((a) => a.id), chosen.id], `« ${chosen.title} » lié au ticket.`);
        }
      });
  }

  protected unlink(article: ArticleSummary): void {
    const ids = (this.articles() ?? []).filter((a) => a.id !== article.id).map((a) => a.id);
    this.save(ids, 'Article délié du ticket.');
  }

  protected draft(): void {
    this.busy.set(true);
    this.drafting.set(true);
    this.api.fromTicket(this.ticket().id).subscribe({
      next: (created) => {
        this.toasts.success('Brouillon créé à partir du ticket : relisez-le avant publication.');
        void this.router.navigate(['/knowledge', created.id, 'edit']);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.drafting.set(false);
        this.toasts.error(toApiProblem(error).detail);
      },
    });
  }

  private save(ids: number[], success: string): void {
    this.busy.set(true);
    this.api.setTicketArticles(this.ticket().id, ids).subscribe({
      next: (articles) => {
        this.busy.set(false);
        this.articles.set(articles);
        this.toasts.success(success);
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.toasts.error(toApiProblem(error).detail);
      },
    });
  }
}
