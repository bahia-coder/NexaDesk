import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import {
  BookOpen,
  LucideAngularModule,
  Plus,
  RefreshCw,
  Search,
  SearchX,
  Sparkles,
  TicketPlus,
  TrendingUp,
  TriangleAlert,
  X,
} from 'lucide-angular';
import { Subject, catchError, debounceTime, distinctUntilChanged, map, of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { dataOf, loadState } from '../../core/http/load-state';
import { categoryIcon } from '../../shared/ui/category-icon';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PaginatorComponent } from '../../shared/ui/paginator.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { ArticleCardComponent } from './article-card.component';
import {
  ARTICLE_SORT_LABELS,
  ArticleFilter,
  ArticleSort,
  ArticleStatus,
  ArticleSummary,
  CategoryCount,
} from './knowledge.models';
import { KnowledgeService } from './knowledge.service';

const PAGE_SIZE = 12;
const POPULAR = 4;
const STATUSES: ArticleStatus[] = ['PUBLISHED', 'DRAFT', 'ARCHIVED'];
const SORTS: ArticleSort[] = ['RELEVANCE', 'RECENT', 'POPULAR'];

function filterFromParams(p: Params): ArticleFilter & { page: number } {
  return {
    search: typeof p['search'] === 'string' && p['search'].trim() ? p['search'].trim() : undefined,
    categoryId: Number(p['categoryId']) > 0 ? Number(p['categoryId']) : undefined,
    status: STATUSES.find((s) => s === p['status']),
    sort: SORTS.find((s) => s === p['sort']),
    page: Math.max(0, Number(p['page']) || 0),
  };
}

/**
 * Knowledge base. Without a search: browse by theme and the most read articles. With one: full-text results,
 * best first. Support staff also see their drafts (technicians) or every article (admins), and write some.
 * Filters in the URL.
 */
@Component({
  selector: 'nx-knowledge-page',
  imports: [
    RouterLink,
    LucideAngularModule,
    ArticleCardComponent,
    EmptyStateComponent,
    PaginatorComponent,
    SkeletonComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './knowledge.page.html',
  styleUrls: ['./knowledge.scss', './knowledge-list.scss'],
})
export class KnowledgePage {
  private readonly api = inject(KnowledgeService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly icons = {
    BookOpen,
    Plus,
    RefreshCw,
    Search,
    SearchX,
    Sparkles,
    TicketPlus,
    TrendingUp,
    TriangleAlert,
    X,
  };
  protected readonly sortLabels = ARTICLE_SORT_LABELS;
  protected readonly sorts = SORTS;

  protected readonly staff = computed(() => this.auth.role() !== 'EMPLOYEE');
  /** Technicians see the published articles and their own drafts; admins every state. */
  protected readonly statusTabs = computed(() =>
    this.auth.role() === 'ADMIN'
      ? [
          { value: undefined, label: 'Tous' },
          { value: 'PUBLISHED' as ArticleStatus, label: 'Publiés' },
          { value: 'DRAFT' as ArticleStatus, label: 'Brouillons' },
          { value: 'ARCHIVED' as ArticleStatus, label: 'Archivés' },
        ]
      : [
          { value: undefined, label: 'Tous' },
          { value: 'PUBLISHED' as ArticleStatus, label: 'Publiés' },
          { value: 'DRAFT' as ArticleStatus, label: 'Mes brouillons' },
        ],
  );

  protected readonly filter = toSignal(this.route.queryParams.pipe(map(filterFromParams)), {
    initialValue: filterFromParams({}),
  });
  /** No search, no theme, no state: the page offers ways in rather than a bare list. */
  protected readonly browsing = computed(() => {
    const f = this.filter();
    return !f.search && !f.categoryId && !f.status;
  });
  /** What the API sorts by: relevance only applies to a search. */
  protected readonly sort = computed<ArticleSort>(() => {
    const f = this.filter();
    return f.sort && (f.sort !== 'RELEVANCE' || f.search)
      ? f.sort
      : f.search
        ? 'RELEVANCE'
        : 'RECENT';
  });

  protected readonly themes = toSignal(
    this.api.categoryCounts().pipe(catchError(() => of(null as CategoryCount[] | null))),
    { initialValue: undefined },
  );
  protected readonly activeTheme = computed(() => {
    const id = this.filter().categoryId;
    return id ? (this.themes()?.find((t) => t.category.id === id)?.category ?? null) : null;
  });

  private readonly reload = signal(0);
  protected readonly state = loadState(
    computed(() => ({ f: this.filter(), attempt: this.reload() })),
    ({ f }) => {
      const { page, ...filter } = f;
      return this.api.list(filter, page, PAGE_SIZE);
    },
  );
  protected readonly page = computed(() => dataOf(this.state()));

  private readonly popularState = loadState(
    computed(() => this.browsing()),
    (browsing) =>
      browsing
        ? this.api.list({ sort: 'POPULAR', status: 'PUBLISHED' }, 0, POPULAR).pipe(
            map((p) => p.content),
            catchError(() => of([] as ArticleSummary[])),
          )
        : of([] as ArticleSummary[]),
  );
  protected readonly popular = computed(() => dataOf(this.popularState()));

  private readonly search$ = new Subject<string>();

  constructor() {
    this.search$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((search) => this.update({ search: search.trim() || null }));
  }

  protected onSearch(value: string): void {
    this.search$.next(value);
  }

  protected clearSearch(input: HTMLInputElement): void {
    input.value = '';
    this.search$.next('');
    input.focus();
  }

  protected update(change: Params): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { ...change, page: null },
      queryParamsHandling: 'merge',
    });
  }

  protected reset(): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  protected goTo(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: page || null },
      queryParamsHandling: 'merge',
    });
  }

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }

  protected icon(name: string | null): ReturnType<typeof categoryIcon> {
    return categoryIcon(name);
  }
}
