import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import {
  BookOpen,
  Eye,
  LucideAngularModule,
  Plus,
  RefreshCw,
  Search,
  SearchX,
  ThumbsUp,
  TriangleAlert,
  X,
} from 'lucide-angular';
import { Subject, catchError, debounceTime, distinctUntilChanged, map, of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { formatDateTime } from '../../core/format/time';
import { dataOf, loadState } from '../../core/http/load-state';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PaginatorComponent } from '../../shared/ui/paginator.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { Category } from '../tickets/tickets.models';
import { TicketsService } from '../tickets/tickets.service';
import {
  ARTICLE_SORT_LABELS,
  ARTICLE_STATUS_LABELS,
  ARTICLE_STATUS_TONES,
  ArticleFilter,
  ArticleSort,
  ArticleStatus,
} from './knowledge.models';
import { KnowledgeService } from './knowledge.service';

const PAGE_SIZE = 20;
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
 * Knowledge base: full-text search for everyone; support staff also see their drafts (technicians) or every
 * article (admins) and can write one. Filters in the URL.
 */
@Component({
  selector: 'nx-knowledge-page',
  imports: [
    RouterLink,
    LucideAngularModule,
    EmptyStateComponent,
    PaginatorComponent,
    SkeletonComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './knowledge.page.html',
  styleUrl: './knowledge.scss',
})
export class KnowledgePage {
  private readonly api = inject(KnowledgeService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly icons = {
    BookOpen,
    Eye,
    Plus,
    RefreshCw,
    Search,
    SearchX,
    ThumbsUp,
    TriangleAlert,
    X,
  };
  protected readonly statusLabels = ARTICLE_STATUS_LABELS;
  protected readonly statusTones = ARTICLE_STATUS_TONES;
  protected readonly sortLabels = ARTICLE_SORT_LABELS;
  protected readonly sorts = SORTS;

  protected readonly staff = computed(() => this.auth.role() !== 'EMPLOYEE');
  /** Technicians filter on their own drafts; admins on any state. */
  protected readonly statusOptions = computed(() =>
    this.auth.role() === 'ADMIN'
      ? STATUSES.map((s) => ({ value: s, label: ARTICLE_STATUS_LABELS[s] + 's' }))
      : [
          { value: 'PUBLISHED' as ArticleStatus, label: 'Publiés' },
          { value: 'DRAFT' as ArticleStatus, label: 'Mes brouillons' },
        ],
  );

  protected readonly filter = toSignal(this.route.queryParams.pipe(map(filterFromParams)), {
    initialValue: filterFromParams({}),
  });
  protected readonly filtered = computed(() => {
    const f = this.filter();
    return !!(f.search || f.categoryId || f.status);
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

  protected readonly categories = toSignal(
    inject(TicketsService)
      .categories()
      .pipe(catchError(() => of([] as Category[]))),
    { initialValue: [] as Category[] },
  );

  private readonly reload = signal(0);
  protected readonly state = loadState(
    computed(() => ({ f: this.filter(), attempt: this.reload() })),
    ({ f }) => {
      const { page, ...filter } = f;
      return this.api.list(filter, page, PAGE_SIZE);
    },
  );
  protected readonly page = computed(() => dataOf(this.state()));

  private readonly search$ = new Subject<string>();

  constructor() {
    this.search$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe((search) => this.update({ search: search.trim() || null }));
  }

  protected onSearch(value: string): void {
    this.search$.next(value);
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

  protected exact(at: string): string {
    return formatDateTime(at);
  }
}
