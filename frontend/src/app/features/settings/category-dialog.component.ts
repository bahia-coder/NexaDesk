import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { toApiProblem } from '../../core/http/api-error';
import { SpinnerComponent } from '../../shared/ui/spinner.component';
import { Category } from '../tickets/tickets.models';
import { ICON_PATTERN } from './settings.models';
import { SettingsService } from './settings.service';

/** Create or edit a category; its keywords feed the local (heuristic) ticket classifier. */
@Component({
  selector: 'nx-category-dialog',
  imports: [ReactiveFormsModule, SpinnerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="dialog" [formGroup]="form" (ngSubmit)="save()" novalidate>
      <h2 id="nx-category-title" class="card__title">
        {{ category ? 'Modifier la catégorie' : 'Nouvelle catégorie' }}
      </h2>
      <label class="field">
        <span class="field__label">Nom *</span>
        <input
          class="input"
          formControlName="name"
          maxlength="80"
          cdkFocusInitial
          [attr.aria-invalid]="form.controls.name.invalid && form.controls.name.touched"
        />
        @if (form.controls.name.invalid && form.controls.name.touched) {
          <span class="field__error">Le nom est obligatoire.</span>
        }
      </label>
      <label class="field">
        <span class="field__label">Description</span>
        <input class="input" formControlName="description" maxlength="255" />
      </label>
      <label class="field">
        <span class="field__label">Icône</span>
        <input
          class="input mono"
          formControlName="icon"
          maxlength="40"
          placeholder="ex. wifi, printer, key-round"
        />
        @if (form.controls.icon.invalid) {
          <span class="field__error">Un nom d’icône Lucide en minuscules (ex. « key-round »).</span>
        }
      </label>
      <label class="field">
        <span class="field__label">Mots-clés</span>
        <textarea
          class="textarea"
          formControlName="keywords"
          maxlength="1000"
          rows="3"
          placeholder="imprimante, impression, toner, bourrage"
        ></textarea>
        <span class="field__hint">
          Séparés par des virgules. L’assistant les utilise pour suggérer cette catégorie à la
          création d’un ticket.
        </span>
      </label>
      @if (error(); as message) {
        <p class="field__error" role="alert">{{ message }}</p>
      }
      <div class="actions">
        <button type="button" class="btn btn--secondary" (click)="ref.close()">Annuler</button>
        <button type="submit" class="btn btn--primary" [disabled]="saving()">
          @if (saving()) {
            <nx-spinner />
          }
          Enregistrer
        </button>
      </div>
    </form>
  `,
  styles: `
    .dialog {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
      width: 520px;
      max-width: 100%;
      padding: var(--space-6);
      border-radius: var(--radius-lg);
      background: var(--color-surface-raised);
      box-shadow: var(--shadow-lg);
    }

    .actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--space-2);
    }
  `,
})
export class CategoryDialogComponent {
  protected readonly category = inject<Category | null>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<Category>>(DialogRef);
  private readonly api = inject(SettingsService);

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: [this.category?.name ?? '', [Validators.required, Validators.maxLength(80)]],
    description: [this.category?.description ?? '', Validators.maxLength(255)],
    icon: [this.category?.icon ?? 'tag', Validators.pattern(ICON_PATTERN)],
    keywords: [this.category?.keywords ?? '', Validators.maxLength(1000)],
  });

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const body = {
      name: v.name.trim(),
      description: v.description.trim() || null,
      icon: v.icon.trim() || null,
      keywords: v.keywords.trim() || null,
    };
    this.saving.set(true);
    this.error.set(null);
    (this.category
      ? this.api.updateCategory(this.category.id, body)
      : this.api.createCategory(body)
    ).subscribe({
      next: (saved) => this.ref.close(saved),
      error: (error: unknown) => {
        this.saving.set(false);
        this.error.set(toApiProblem(error).detail);
      },
    });
  }
}
