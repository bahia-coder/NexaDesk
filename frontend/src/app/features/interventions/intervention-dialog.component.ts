import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { toApiProblem } from '../../core/http/api-error';
import { SpinnerComponent } from '../../shared/ui/spinner.component';
import { Intervention } from './interventions.models';
import { InterventionsService } from './interventions.service';

export interface InterventionDialogData {
  ticketId: number;
  reference: string;
  /** Present when editing. */
  intervention?: Intervention;
}

const QUICK_DURATIONS = [15, 30, 60, 120];

/** Log or edit an intervention: diagnosis, solution (optional) and time spent. Saves, then closes. */
@Component({
  selector: 'nx-intervention-dialog',
  imports: [ReactiveFormsModule, SpinnerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="dialog" [formGroup]="form" (ngSubmit)="save()" novalidate>
      <h2 id="nx-intervention-title" class="card__title">
        {{ data.intervention ? 'Modifier l’intervention' : 'Nouvelle intervention' }} ·
        {{ data.reference }}
      </h2>

      <label class="field">
        <span class="field__label">Diagnostic *</span>
        <textarea
          class="textarea"
          formControlName="diagnosis"
          rows="4"
          maxlength="5000"
          placeholder="Ce qui a été constaté, testé, écarté…"
          [attr.aria-invalid]="invalid('diagnosis')"
          cdkFocusInitial
        ></textarea>
        @if (invalid('diagnosis')) {
          <span class="field__error">Le diagnostic est obligatoire.</span>
        }
      </label>

      <label class="field">
        <span class="field__label">Solution</span>
        <textarea class="textarea" formControlName="solution" rows="3" maxlength="5000"></textarea>
        <span class="field__hint">
          Reprise automatiquement si le ticket est résolu sans autre solution.
        </span>
      </label>

      <div class="field">
        <label class="field__label" for="minutes">Temps passé (minutes) *</label>
        <div class="duration">
          <input
            id="minutes"
            class="input"
            type="number"
            min="1"
            max="1440"
            formControlName="timeSpentMinutes"
            [attr.aria-invalid]="invalid('timeSpentMinutes')"
          />
          @for (minutes of quick; track minutes) {
            <button
              type="button"
              class="btn btn--secondary btn--sm"
              (click)="form.controls.timeSpentMinutes.setValue(minutes)"
            >
              {{ minutes < 60 ? minutes + ' min' : minutes / 60 + ' h' }}
            </button>
          }
        </div>
        @if (invalid('timeSpentMinutes')) {
          <span class="field__error">Entre 1 et 1440 minutes (24 heures).</span>
        }
      </div>

      @if (error(); as message) {
        <p class="field__error" role="alert">{{ message }}</p>
      }

      <div class="dialog__actions">
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
      width: 560px;
      max-width: 100%;
      padding: var(--space-6);
      border-radius: var(--radius-lg);
      background: var(--color-surface-raised);
      box-shadow: var(--shadow-lg);
    }

    .duration {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);

      .input {
        width: 110px;
      }
    }

    .dialog__actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--space-2);
    }
  `,
})
export class InterventionDialogComponent {
  protected readonly data = inject<InterventionDialogData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<Intervention>>(DialogRef);
  private readonly api = inject(InterventionsService);

  protected readonly quick = QUICK_DURATIONS;
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    diagnosis: [
      this.data.intervention?.diagnosis ?? '',
      [Validators.required, Validators.maxLength(5000)],
    ],
    solution: [this.data.intervention?.solution ?? '', Validators.maxLength(5000)],
    timeSpentMinutes: [
      this.data.intervention?.timeSpentMinutes ?? 30,
      [Validators.required, Validators.min(1), Validators.max(1440)],
    ],
  });

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request = {
      diagnosis: v.diagnosis.trim(),
      solution: v.solution.trim() || null,
      timeSpentMinutes: Number(v.timeSpentMinutes),
    };
    this.saving.set(true);
    this.error.set(null);
    const call = this.data.intervention
      ? this.api.update(this.data.ticketId, this.data.intervention.id, request)
      : this.api.add(this.data.ticketId, request);
    call.subscribe({
      next: (saved) => this.ref.close(saved),
      error: (error: unknown) => {
        this.saving.set(false);
        this.error.set(toApiProblem(error).detail);
      },
    });
  }

  protected invalid(name: 'diagnosis' | 'timeSpentMinutes'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }
}
