import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, linkedSignal, signal } from '@angular/core';
import { LucideAngularModule, ThumbsUp, X } from 'lucide-angular';

import { Article } from '../../core/ai/ai.models';
import { AiService } from '../../core/ai/ai.service';
import { formatDateTime } from '../../core/format/time';
import { toApiProblem } from '../../core/http/api-error';
import { dataOf, loadState } from '../../core/http/load-state';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { SpinnerComponent } from '../../shared/ui/spinner.component';
import { MarkdownTextComponent } from './markdown-text.component';

/** Reads a knowledge-base article offered by the assistant; "Cet article m'a aidé" is counted once per user. */
@Component({
  selector: 'nx-article-dialog',
  imports: [LucideAngularModule, MarkdownTextComponent, SkeletonComponent, SpinnerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="dialog" aria-labelledby="nx-article-title">
      <header class="head">
        <h2 id="nx-article-title" class="card__title">{{ article()?.title ?? data.title }}</h2>
        <button
          type="button"
          class="btn btn--ghost btn--icon btn--sm"
          aria-label="Fermer"
          (click)="ref.close()"
        >
          <lucide-icon [img]="icons.X" [size]="18" aria-hidden="true" />
        </button>
      </header>

      @if (article(); as a) {
        <p class="muted small">{{ a.category.name }} · mis à jour le {{ exact(a.updatedAt) }}</p>
        <p class="summary">{{ a.summary }}</p>
        <nx-markdown-text [text]="a.content" />
        <footer class="foot">
          <span class="muted small"
            >{{ a.helpfulCount }} personne{{ a.helpfulCount > 1 ? 's' : '' }} aidée{{
              a.helpfulCount > 1 ? 's' : ''
            }}</span
          >
          <button
            type="button"
            class="btn btn--secondary btn--sm"
            [disabled]="a.helpedByMe || marking()"
            (click)="helpful(a)"
          >
            @if (marking()) {
              <nx-spinner [size]="14" />
            } @else {
              <lucide-icon [img]="icons.ThumbsUp" [size]="14" aria-hidden="true" />
            }
            {{ a.helpedByMe ? 'Merci pour votre retour' : 'Cet article m’a aidé' }}
          </button>
        </footer>
      } @else if (state().status === 'error') {
        <p class="field__error" role="alert">L’article n’a pas pu être chargé.</p>
      } @else {
        <nx-skeleton height="16px" [lines]="8" />
      }
      @if (error(); as message) {
        <p class="field__error" role="alert">{{ message }}</p>
      }
    </article>
  `,
  styles: `
    .dialog {
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
      width: 680px;
      max-width: 100%;
      max-height: calc(100vh - 48px);
      overflow-y: auto;
      padding: var(--space-6);
      border-radius: var(--radius-lg);
      background: var(--color-surface-raised);
      box-shadow: var(--shadow-lg);
    }

    .head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--space-3);
    }

    .summary {
      color: var(--color-text-muted);
    }

    .small {
      font: var(--text-xs);
    }

    .foot {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-3);
      padding-top: var(--space-3);
      border-top: 1px solid var(--color-border);
    }
  `,
})
export class ArticleDialogComponent {
  protected readonly data = inject<{ id: number; title: string }>(DIALOG_DATA);
  protected readonly ref = inject(DialogRef);
  private readonly ai = inject(AiService);

  protected readonly icons = { ThumbsUp, X };
  protected readonly state = loadState(signal(this.data.id), (id) => this.ai.article(id));
  protected readonly article = linkedSignal<Article | null>(() => dataOf(this.state()));
  protected readonly marking = signal(false);
  protected readonly error = signal<string | null>(null);

  protected helpful(article: Article): void {
    this.marking.set(true);
    this.ai.markHelpful(article.id).subscribe({
      next: (result) => {
        this.marking.set(false);
        this.article.set({ ...article, ...result });
      },
      error: (error: unknown) => {
        this.marking.set(false);
        this.error.set(toApiProblem(error).detail);
      },
    });
  }

  protected exact(at: string): string {
    return formatDateTime(at);
  }
}
