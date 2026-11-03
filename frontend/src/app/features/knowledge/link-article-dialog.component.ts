import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { LucideAngularModule, Search } from 'lucide-angular';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  map,
  of,
  startWith,
  switchMap,
} from 'rxjs';

import { ArticleSummary } from './knowledge.models';
import { KnowledgeService } from './knowledge.service';

const RESULTS = 8;

/** Picks a published article to link to a ticket; closes with the chosen article. */
@Component({
  selector: 'nx-link-article-dialog',
  imports: [LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dialog">
      <h2 id="nx-link-article-title" class="card__title">Lier un article</h2>
      <label class="search">
        <lucide-icon [img]="icons.Search" [size]="16" aria-hidden="true" />
        <span class="sr-only">Rechercher un article publié</span>
        <input
          class="input"
          type="search"
          placeholder="Rechercher un article publié…"
          cdkFocusInitial
          (input)="query.set($any($event.target).value)"
        />
      </label>
      @if (results(); as list) {
        @if (list.length === 0) {
          <p class="muted state">Aucun article publié ne correspond.</p>
        } @else {
          <ul class="results" aria-label="Articles publiés">
            @for (a of list; track a.id) {
              <li>
                <button
                  type="button"
                  class="result"
                  [disabled]="data.linkedIds.includes(a.id)"
                  (click)="ref.close(a)"
                >
                  <span class="result__title">{{ a.title }}</span>
                  <span class="muted small">{{ a.category.name }} · {{ a.summary }}</span>
                  @if (data.linkedIds.includes(a.id)) {
                    <span class="muted small">Déjà lié</span>
                  }
                </button>
              </li>
            }
          </ul>
        }
      } @else {
        <p class="muted state">Recherche…</p>
      }
      <div class="actions">
        <button type="button" class="btn btn--secondary" (click)="ref.close()">Annuler</button>
      </div>
    </div>
  `,
  styles: `
    .dialog {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
      width: 560px;
      max-width: 100%;
      padding: var(--space-6);
      border-radius: var(--radius-lg);
      background: var(--color-surface-raised);
      box-shadow: var(--shadow-lg);
    }

    .search {
      position: relative;

      lucide-icon {
        position: absolute;
        top: 50%;
        left: var(--space-3);
        transform: translateY(-50%);
        color: var(--color-text-muted);
      }

      .input {
        padding-left: 34px;
      }
    }

    .results {
      display: flex;
      flex-direction: column;
      max-height: 360px;
      margin: 0;
      padding: 0;
      overflow-y: auto;
      list-style: none;
    }

    .result {
      display: flex;
      flex-direction: column;
      gap: 2px;
      width: 100%;
      padding: var(--space-2) var(--space-3);
      border: 0;
      border-radius: var(--radius-sm);
      background: transparent;
      text-align: left;
      cursor: pointer;

      &:hover:not(:disabled) {
        background: var(--color-surface-hover);
      }

      &:disabled {
        cursor: default;
        opacity: 0.6;
      }
    }

    .result__title {
      font-weight: 600;
    }

    .small {
      font: var(--text-xs);
    }

    .state {
      padding: var(--space-4) 0;
      text-align: center;
    }

    .actions {
      display: flex;
      justify-content: flex-end;
    }
  `,
})
export class LinkArticleDialogComponent {
  protected readonly data = inject<{ linkedIds: number[] }>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<ArticleSummary>>(DialogRef);
  private readonly api = inject(KnowledgeService);

  protected readonly icons = { Search };
  protected readonly query = signal('');

  /** Published articles matching the search; null while a search is in flight. */
  protected readonly results = toSignal(
    toObservable(this.query).pipe(
      map((q) => q.trim()),
      debounceTime(250),
      distinctUntilChanged(),
      switchMap((search) =>
        this.api.list({ search: search || undefined, status: 'PUBLISHED' }, 0, RESULTS).pipe(
          map((page) => page.content as ArticleSummary[] | null),
          catchError(() => of([] as ArticleSummary[])),
          startWith(null),
        ),
      ),
    ),
    { initialValue: null },
  );
}
