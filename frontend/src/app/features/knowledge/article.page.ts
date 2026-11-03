import {
  ChangeDetectionStrategy,
  DOCUMENT,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  Archive,
  ChevronRight,
  Clock,
  Eye,
  LucideAngularModule,
  Pencil,
  RefreshCw,
  Send,
  Sparkles,
  ThumbsDown,
  ThumbsUp,
  TicketPlus,
  TriangleAlert,
} from 'lucide-angular';
import { Observable, catchError, map, of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { outline } from '../../core/format/markdown';
import { formatDateTime } from '../../core/format/time';
import { toApiProblem } from '../../core/http/api-error';
import { dataOf, loadState } from '../../core/http/load-state';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { AvatarComponent } from '../../shared/ui/avatar.component';
import { categoryIcon } from '../../shared/ui/category-icon';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { MarkdownTextComponent } from '../../shared/ui/markdown-text.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { SpinnerComponent } from '../../shared/ui/spinner.component';
import {
  ARTICLE_STATUS_LABELS,
  ARTICLE_STATUS_TONES,
  ArticleAction,
  ArticleSummary,
  KnowledgeArticle,
  readingMinutes,
} from './knowledge.models';
import { ArticleCardComponent } from './article-card.component';
import { KnowledgeService } from './knowledge.service';

/**
 * Reads an article: outline, feedback ("did it help?", with a way out when it did not), related articles. The
 * buttons are the actions the API allows the user (edit, publish, archive, helpful).
 */
@Component({
  selector: 'nx-article-page',
  imports: [
    RouterLink,
    LucideAngularModule,
    ArticleCardComponent,
    AvatarComponent,
    EmptyStateComponent,
    MarkdownTextComponent,
    SkeletonComponent,
    SpinnerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './article.page.html',
  styleUrls: ['./knowledge.scss', './article.scss'],
})
export class ArticlePage {
  private readonly api = inject(KnowledgeService);
  private readonly auth = inject(AuthService);
  private readonly confirmService = inject(ConfirmService);
  private readonly toasts = inject(ToastService);
  private readonly document = inject(DOCUMENT);

  /** Route parameter (component input binding). */
  readonly id = input.required<string>();

  protected readonly icons = {
    Archive,
    ChevronRight,
    Clock,
    Eye,
    Pencil,
    RefreshCw,
    Send,
    Sparkles,
    ThumbsDown,
    ThumbsUp,
    TicketPlus,
    TriangleAlert,
  };
  protected readonly anchorPrefix = 'section-';
  protected readonly statusLabels = ARTICLE_STATUS_LABELS;
  protected readonly statusTones = ARTICLE_STATUS_TONES;
  protected readonly staff = computed(() => this.auth.role() !== 'EMPLOYEE');

  private readonly reload = signal(0);
  protected readonly state = loadState(
    computed(() => ({ id: Number(this.id()), attempt: this.reload() })),
    ({ id }) => this.api.get(id),
  );
  /** Replaced by each action's response. */
  protected readonly article = linkedSignal<KnowledgeArticle | null>(() => dataOf(this.state()));
  protected readonly running = signal<ArticleAction | null>(null);

  protected readonly outline = computed(() => outline(this.article()?.content ?? ''));
  protected readonly minutes = computed(() => readingMinutes(this.article()?.content ?? ''));
  protected readonly icon = computed(() => categoryIcon(this.article()?.category.icon));
  /** "Non" to "did it help?": not sent anywhere, it offers the assistant and a ticket instead. */
  protected readonly notHelped = signal(false);

  /** Other published articles of the same theme, most read first. */
  private readonly relatedState = loadState(
    // Same article, same theme: no new search when a vote or a publication replaces the article object.
    computed(
      () => {
        const a = this.article();
        return a && a.status === 'PUBLISHED' ? { id: a.id, categoryId: a.category.id } : null;
      },
      { equal: (x, y) => x?.id === y?.id && x?.categoryId === y?.categoryId },
    ),
    (current) =>
      current
        ? this.api
            .list({ categoryId: current.categoryId, status: 'PUBLISHED', sort: 'POPULAR' }, 0, 4)
            .pipe(
              map((page) => page.content.filter((a) => a.id !== current.id).slice(0, 3)),
              catchError(() => of([] as ArticleSummary[])),
            )
        : of([] as ArticleSummary[]),
  );
  protected readonly related = computed(() => dataOf(this.relatedState()) ?? []);

  protected scrollTo(id: string): void {
    this.document
      .getElementById(this.anchorPrefix + id)
      ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  protected can(action: ArticleAction): boolean {
    return this.article()?.availableActions.includes(action) ?? false;
  }

  protected publish(a: KnowledgeArticle): void {
    this.confirmService
      .confirm({
        title: 'Publier l’article ?',
        message:
          'Il sera visible par tous les employés, proposé par l’assistant et lors de la création des tickets.',
        confirmLabel: 'Publier',
      })
      .subscribe((ok) => {
        if (ok) {
          this.run('PUBLISH', this.api.publish(a.id), 'Article publié.');
        }
      });
  }

  protected archive(a: KnowledgeArticle): void {
    this.confirmService
      .confirm({
        title: 'Archiver l’article ?',
        message:
          'Il disparaîtra de la recherche et des suggestions. Vous pourrez le publier de nouveau plus tard.',
        confirmLabel: 'Archiver',
        tone: 'danger',
      })
      .subscribe((ok) => {
        if (ok) {
          this.run('ARCHIVE', this.api.archive(a.id), 'Article archivé.');
        }
      });
  }

  protected helpful(a: KnowledgeArticle): void {
    this.running.set('MARK_HELPFUL');
    this.api.markHelpful(a.id).subscribe({
      next: (result) => {
        this.running.set(null);
        this.article.set({ ...a, ...result });
      },
      error: (error: unknown) => {
        this.running.set(null);
        this.toasts.error(toApiProblem(error).detail);
      },
    });
  }

  private run(action: ArticleAction, request: Observable<KnowledgeArticle>, success: string): void {
    this.running.set(action);
    request.subscribe({
      next: (updated) => {
        this.running.set(null);
        this.article.set(updated);
        this.toasts.success(success);
      },
      error: (error: unknown) => {
        this.running.set(null);
        this.toasts.error(toApiProblem(error).detail);
      },
    });
  }

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }

  protected exact(at: string | null): string {
    return at ? formatDateTime(at) : '—';
  }
}
