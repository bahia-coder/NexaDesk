import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ArrowLeft, LucideAngularModule, RefreshCw, TriangleAlert } from 'lucide-angular';
import { catchError, map, of } from 'rxjs';

import { toApiProblem } from '../../core/http/api-error';
import { dataOf, loadState } from '../../core/http/load-state';
import { ToastService } from '../../core/services/toast.service';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { MarkdownTextComponent } from '../../shared/ui/markdown-text.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { SpinnerComponent } from '../../shared/ui/spinner.component';
import { Category } from '../tickets/tickets.models';
import { TicketsService } from '../tickets/tickets.service';
import { ARTICLE_LIMITS, ArticleFields, KnowledgeArticle } from './knowledge.models';
import { KnowledgeService } from './knowledge.service';

const TEMPLATE = `## Symptômes

Ce que l’utilisateur constate.

## Solution

1. Première étape.
2. Deuxième étape.

## Si le problème persiste

Ouvrez un ticket en précisant…`;

/**
 * Writes a new draft or edits an article (technicians: their drafts; admins: any article), with a live
 * preview of the content. Saving sends the version read, so a concurrent edit is refused instead of lost.
 */
@Component({
  selector: 'nx-article-editor-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    LucideAngularModule,
    EmptyStateComponent,
    MarkdownTextComponent,
    SkeletonComponent,
    SpinnerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <a class="back" [routerLink]="id() ? ['/knowledge', id()] : '/knowledge'">
        <lucide-icon [img]="icons.ArrowLeft" [size]="16" aria-hidden="true" />
        {{ id() ? 'Retour à l’article' : 'Base de connaissances' }}
      </a>

      @if (state().status === 'error') {
        <div class="card">
          <nx-empty-state title="Article introuvable" [icon]="icons.TriangleAlert">
            <button type="button" class="btn btn--secondary" (click)="retry()">
              <lucide-icon [img]="icons.RefreshCw" [size]="16" aria-hidden="true" /> Réessayer
            </button>
          </nx-empty-state>
        </div>
      } @else if (state().status === 'loading') {
        <nx-skeleton height="420px" />
      } @else if (readOnly()) {
        <div class="card">
          <nx-empty-state
            title="Article non modifiable"
            description="Un technicien modifie ses brouillons ; un administrateur, tous les articles."
            [icon]="icons.TriangleAlert"
          />
        </div>
      } @else {
        <form class="stack" [formGroup]="form" (ngSubmit)="save()" novalidate>
          <header class="page__header">
            <h1 class="page-title">{{ id() ? 'Modifier l’article' : 'Nouvel article' }}</h1>
            <div class="form-actions">
              <a
                class="btn btn--secondary"
                [routerLink]="id() ? ['/knowledge', id()] : '/knowledge'"
                >Annuler</a
              >
              <button type="submit" class="btn btn--primary" [disabled]="saving()">
                @if (saving()) {
                  <nx-spinner />
                }
                {{ id() ? 'Enregistrer' : 'Enregistrer le brouillon' }}
              </button>
            </div>
          </header>

          <section class="card panel">
            <div class="grid-2">
              <label class="field">
                <span class="field__label">Titre *</span>
                <input
                  class="input"
                  formControlName="title"
                  [maxlength]="limits.titleMax"
                  [attr.aria-invalid]="invalid('title')"
                />
                @if (errors()['title']; as message) {
                  <span class="field__error">{{ message }}</span>
                } @else if (invalid('title')) {
                  <span class="field__error"
                    >Entre {{ limits.titleMin }} et {{ limits.titleMax }} caractères.</span
                  >
                }
              </label>
              <label class="field">
                <span class="field__label">Catégorie *</span>
                <select
                  class="select"
                  formControlName="categoryId"
                  [attr.aria-invalid]="invalid('categoryId')"
                >
                  <option [ngValue]="0" disabled>Choisir…</option>
                  @for (c of categories(); track c.id) {
                    <option [ngValue]="c.id">{{ c.name }}</option>
                  }
                </select>
                @if (invalid('categoryId')) {
                  <span class="field__error">La catégorie est obligatoire.</span>
                }
              </label>
            </div>
            <label class="field">
              <span class="field__label">Résumé *</span>
              <textarea
                class="input"
                rows="2"
                formControlName="summary"
                [maxlength]="limits.summaryMax"
                [attr.aria-invalid]="invalid('summary')"
              ></textarea>
              @if (errors()['summary']; as message) {
                <span class="field__error">{{ message }}</span>
              } @else if (invalid('summary')) {
                <span class="field__error">Le résumé est obligatoire.</span>
              } @else {
                <span class="field__hint"
                  >Affiché dans les résultats de recherche et par l’assistant.</span
                >
              }
            </label>
          </section>

          <div class="editor">
            <section class="card panel">
              <label class="field">
                <span class="field__label">Contenu *</span>
                <textarea
                  class="input"
                  formControlName="content"
                  [maxlength]="limits.contentMax"
                  [attr.aria-invalid]="invalid('content')"
                ></textarea>
                @if (errors()['content']; as message) {
                  <span class="field__error">{{ message }}</span>
                } @else if (invalid('content')) {
                  <span class="field__error">Au moins {{ limits.contentMin }} caractères.</span>
                } @else {
                  <span class="field__hint">
                    « ## Titre » pour une section, « 1. » pour une étape, « - » pour une puce.
                  </span>
                }
              </label>
              <p class="counter">{{ content().length }} / {{ limits.contentMax }}</p>
            </section>
            <section class="card panel preview" aria-label="Aperçu">
              <h2 class="card__title">Aperçu</h2>
              @if (content().trim()) {
                <nx-markdown-text [text]="content()" />
              } @else {
                <p class="muted">Le contenu mis en forme s’affichera ici.</p>
              }
            </section>
          </div>

          @if (error(); as message) {
            <p class="field__error" role="alert">{{ message }}</p>
          }
        </form>
      }
    </div>
  `,
  styleUrls: ['../users/users.scss', './knowledge.scss'],
})
export class ArticleEditorPage {
  private readonly api = inject(KnowledgeService);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);

  /** Route parameter; absent for a new article. */
  readonly id = input<string>();

  protected readonly icons = { ArrowLeft, RefreshCw, TriangleAlert };
  protected readonly limits = ARTICLE_LIMITS;

  protected readonly categories = toSignal(
    inject(TicketsService)
      .categories()
      .pipe(catchError(() => of([] as Category[]))),
    { initialValue: [] as Category[] },
  );

  private readonly reload = signal(0);
  protected readonly state = loadState(
    computed(() => ({ id: Number(this.id()) || null, attempt: this.reload() })),
    ({ id }) => (id ? this.api.get(id) : of(null as KnowledgeArticle | null)),
  );

  /** The API decides who may edit (KnowledgePolicy): a published article is the admins' only. */
  protected readonly readOnly = computed(() => {
    const article = dataOf(this.state());
    return !!article && !article.availableActions.includes('EDIT');
  });

  protected readonly form = inject(NonNullableFormBuilder).group({
    title: [
      '',
      [
        Validators.required,
        Validators.minLength(ARTICLE_LIMITS.titleMin),
        Validators.maxLength(ARTICLE_LIMITS.titleMax),
      ],
    ],
    summary: ['', [Validators.required, Validators.maxLength(ARTICLE_LIMITS.summaryMax)]],
    categoryId: [0, Validators.min(1)],
    content: [
      TEMPLATE,
      [
        Validators.required,
        Validators.minLength(ARTICLE_LIMITS.contentMin),
        Validators.maxLength(ARTICLE_LIMITS.contentMax),
      ],
    ],
  });
  protected readonly content = toSignal(
    this.form.controls.content.valueChanges.pipe(map((value) => value ?? '')),
    { initialValue: this.form.controls.content.value },
  );

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Field errors returned by the API (400 VALIDATION_FAILED). */
  protected readonly errors = signal<Record<string, string>>({});
  private version = 0;

  constructor() {
    // Fill the form with the article being edited.
    effect(() => {
      const article = dataOf(this.state());
      if (article) {
        untracked(() => {
          this.version = article.version;
          this.form.reset({
            title: article.title,
            summary: article.summary,
            categoryId: article.category.id,
            content: article.content,
          });
        });
      }
    });
  }

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const fields: ArticleFields = {
      title: v.title.trim(),
      summary: v.summary.trim(),
      content: v.content.trim(),
      categoryId: v.categoryId,
    };
    const id = Number(this.id()) || null;
    this.saving.set(true);
    this.error.set(null);
    this.errors.set({});
    (id ? this.api.update(id, fields, this.version) : this.api.create(fields)).subscribe({
      next: (saved) => {
        this.form.markAsPristine();
        this.toasts.success(id ? 'Article enregistré.' : 'Brouillon enregistré.');
        void this.router.navigate(['/knowledge', saved.id]);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        const problem = toApiProblem(error);
        this.errors.set(Object.fromEntries(problem.errors.map((e) => [e.field, e.message])));
        this.error.set(problem.detail);
      },
    });
  }

  protected invalid(name: 'title' | 'summary' | 'categoryId' | 'content'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }
}
