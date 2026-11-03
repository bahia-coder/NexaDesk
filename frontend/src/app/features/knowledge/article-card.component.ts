import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Eye, LucideAngularModule, ThumbsUp } from 'lucide-angular';

import { formatDateTime } from '../../core/format/time';
import { categoryIcon } from '../../shared/ui/category-icon';
import { ARTICLE_STATUS_LABELS, ARTICLE_STATUS_TONES, ArticleSummary } from './knowledge.models';

/** An article in a list: category icon, title (the whole card opens it), summary and popularity. */
@Component({
  selector: 'nx-article-card',
  imports: [RouterLink, LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.compact]': 'compact()' },
  template: `
    @let a = article();
    <span class="icon" aria-hidden="true">
      <lucide-icon [img]="icon()" [size]="compact() ? 18 : 20" />
    </span>
    <span class="body">
      <span class="title-row">
        <a class="title" [routerLink]="['/knowledge', a.id]">{{ a.title }}</a>
        @if (a.status !== 'PUBLISHED') {
          <span [class]="'badge badge--' + statusTones[a.status]">{{
            statusLabels[a.status]
          }}</span>
        }
      </span>
      <span class="summary">{{ a.summary }}</span>
      <span class="meta">
        <span>{{ a.category.name }}</span>
        @if (a.status === 'PUBLISHED') {
          <span
            ><lucide-icon [img]="icons.Eye" [size]="12" aria-hidden="true" /> {{ a.viewCount }}
            <span class="sr-only">consultations</span></span
          >
          <span
            ><lucide-icon [img]="icons.ThumbsUp" [size]="12" aria-hidden="true" />
            {{ a.helpfulCount }} <span class="sr-only">personnes aidées</span></span
          >
        }
        @if (!compact()) {
          <span>Mis à jour le {{ updated() }}</span>
        }
      </span>
    </span>
  `,
  styles: `
    :host {
      position: relative;
      display: flex;
      gap: var(--space-3);
      padding: var(--space-4);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-lg);
      background: var(--color-surface);
      transition:
        border-color 0.15s,
        box-shadow 0.15s,
        transform 0.15s;

      &:hover,
      &:focus-within {
        border-color: var(--color-primary);
        box-shadow: var(--shadow-md);
      }

      @media (prefers-reduced-motion: no-preference) {
        &:hover {
          transform: translateY(-1px);
        }
      }
    }

    :host(.compact) {
      padding: var(--space-3);
    }

    .icon {
      display: grid;
      flex: none;
      place-items: center;
      width: 40px;
      height: 40px;
      border-radius: var(--radius-md);
      background: var(--color-primary-subtle);
      color: var(--color-primary);
    }

    :host(.compact) .icon {
      width: 34px;
      height: 34px;
    }

    .body {
      display: flex;
      flex: 1;
      flex-direction: column;
      gap: var(--space-1);
      min-width: 0;
    }

    .title-row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--space-2);
    }

    .title {
      font-weight: 600;
      color: var(--color-text);
      text-decoration: none;

      /* The whole card is the link's hit area. */
      &::after {
        content: '';
        position: absolute;
        inset: 0;
        border-radius: inherit;
      }

      &:focus-visible {
        outline: none;
      }
    }

    :host(:focus-within) {
      outline: 2px solid var(--color-primary);
      outline-offset: 2px;
    }

    .summary {
      display: -webkit-box;
      overflow: hidden;
      -webkit-box-orient: vertical;
      -webkit-line-clamp: 2;
      color: var(--color-text-muted);
      font: var(--text-small);
    }

    .meta {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--space-1) var(--space-3);
      margin-top: var(--space-1);
      font: var(--text-xs);
      color: var(--color-text-muted);

      > span {
        display: inline-flex;
        align-items: center;
        gap: 4px;
      }
    }
  `,
})
export class ArticleCardComponent {
  readonly article = input.required<ArticleSummary>();
  /** Smaller, without the date: for the "most read" tiles and related articles. */
  readonly compact = input(false);

  protected readonly icons = { Eye, ThumbsUp };
  protected readonly statusLabels = ARTICLE_STATUS_LABELS;
  protected readonly statusTones = ARTICLE_STATUS_TONES;
  protected readonly icon = computed(() => categoryIcon(this.article().category.icon));
  protected readonly updated = computed(() =>
    formatDateTime(this.article().updatedAt).slice(0, 10),
  );
}
