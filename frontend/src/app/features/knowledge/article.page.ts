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
import {
  Archive,
  ArrowLeft,
  LucideAngularModule,
  Pencil,
  RefreshCw,
  Send,
  ThumbsUp,
  TriangleAlert,
} from 'lucide-angular';
import { Observable } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { formatDateTime } from '../../core/format/time';
import { toApiProblem } from '../../core/http/api-error';
import { dataOf, loadState } from '../../core/http/load-state';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { AvatarComponent } from '../../shared/ui/avatar.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { MarkdownTextComponent } from '../../shared/ui/markdown-text.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { SpinnerComponent } from '../../shared/ui/spinner.component';
import {
  ARTICLE_STATUS_LABELS,
  ARTICLE_STATUS_TONES,
  ArticleAction,
  KnowledgeArticle,
} from './knowledge.models';
import { KnowledgeService } from './knowledge.service';

/** Reads an article; the buttons are the actions the API allows the user (edit, publish, archive, helpful). */
@Component({
  selector: 'nx-article-page',
  imports: [
    RouterLink,
    LucideAngularModule,
    AvatarComponent,
    EmptyStateComponent,
    MarkdownTextComponent,
    SkeletonComponent,
    SpinnerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './article.page.html',
  styleUrl: './knowledge.scss',
})
export class ArticlePage {
  private readonly api = inject(KnowledgeService);
  private readonly auth = inject(AuthService);
  private readonly confirmService = inject(ConfirmService);
  private readonly toasts = inject(ToastService);

  /** Route parameter (component input binding). */
  readonly id = input.required<string>();

  protected readonly icons = {
    Archive,
    ArrowLeft,
    Pencil,
    RefreshCw,
    Send,
    ThumbsUp,
    TriangleAlert,
  };
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
