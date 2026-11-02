import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { toApiProblem } from '../../core/http/api-error';
import { ROLE_LABELS, Role } from '../../core/models/user.models';
import { SpinnerComponent } from '../../shared/ui/spinner.component';
import { Department, PHONE_PATTERN, TemporaryPassword } from './users.models';
import { UsersService } from './users.service';

export interface UserFormDialogData {
  departments: Department[];
}

/** New account: identity, role, department. The server answers with a one-time temporary password. */
@Component({
  selector: 'nx-user-form-dialog',
  imports: [ReactiveFormsModule, SpinnerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="dialog" [formGroup]="form" (ngSubmit)="save()" novalidate>
      <h2 id="nx-user-form-title" class="card__title">Nouvel utilisateur</h2>
      <div class="grid">
        <label class="field">
          <span class="field__label">Prénom *</span>
          <input
            class="input"
            formControlName="firstName"
            maxlength="80"
            autocomplete="off"
            cdkFocusInitial
            [attr.aria-invalid]="invalid('firstName')"
          />
          @if (invalid('firstName')) {
            <span class="field__error">Le prénom est obligatoire.</span>
          }
        </label>
        <label class="field">
          <span class="field__label">Nom *</span>
          <input
            class="input"
            formControlName="lastName"
            maxlength="80"
            autocomplete="off"
            [attr.aria-invalid]="invalid('lastName')"
          />
          @if (invalid('lastName')) {
            <span class="field__error">Le nom est obligatoire.</span>
          }
        </label>
      </div>
      <label class="field">
        <span class="field__label">E-mail professionnel *</span>
        <input
          class="input"
          type="email"
          formControlName="email"
          maxlength="255"
          autocomplete="off"
          [attr.aria-invalid]="invalid('email') || !!serverErrors()['email']"
        />
        @if (serverErrors()['email']; as message) {
          <span class="field__error">{{ message }}</span>
        } @else if (invalid('email')) {
          <span class="field__error">Saisissez une adresse e-mail valide.</span>
        }
      </label>
      <div class="grid">
        <label class="field">
          <span class="field__label">Rôle *</span>
          <select class="select" formControlName="role">
            @for (r of roles; track r) {
              <option [value]="r">{{ roleLabels[r] }}</option>
            }
          </select>
        </label>
        <label class="field">
          <span class="field__label">Service</span>
          <select class="select" formControlName="departmentId">
            <option [ngValue]="0">Aucun</option>
            @for (d of data.departments; track d.id) {
              <option [ngValue]="d.id">{{ d.name }}</option>
            }
          </select>
        </label>
      </div>
      <div class="grid">
        <label class="field">
          <span class="field__label">Poste</span>
          <input class="input" formControlName="jobTitle" maxlength="120" />
        </label>
        <label class="field">
          <span class="field__label">Téléphone</span>
          <input
            class="input"
            type="tel"
            formControlName="phone"
            maxlength="30"
            [attr.aria-invalid]="invalid('phone')"
          />
          @if (invalid('phone')) {
            <span class="field__error">Le numéro de téléphone n’est pas valide.</span>
          }
        </label>
      </div>
      <p class="field__hint">
        Un mot de passe temporaire sera généré : l’utilisateur devra le changer à sa première
        connexion.
      </p>
      @if (error(); as message) {
        <p class="field__error" role="alert">{{ message }}</p>
      }
      <div class="actions">
        <button type="button" class="btn btn--secondary" (click)="ref.close()">Annuler</button>
        <button type="submit" class="btn btn--primary" [disabled]="saving()">
          @if (saving()) {
            <nx-spinner />
          }
          Créer le compte
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

    .grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: var(--space-4);

      @media (width < 640px) {
        grid-template-columns: 1fr;
      }
    }

    .actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--space-2);
    }
  `,
})
export class UserFormDialogComponent {
  protected readonly data = inject<UserFormDialogData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<TemporaryPassword>>(DialogRef);
  private readonly api = inject(UsersService);

  protected readonly roles: Role[] = ['EMPLOYEE', 'TECHNICIAN', 'ADMIN'];
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Field errors returned by the API (400 VALIDATION_FAILED, 409 e-mail already used). */
  protected readonly serverErrors = signal<Record<string, string>>({});

  protected readonly form = inject(NonNullableFormBuilder).group({
    firstName: ['', [Validators.required, Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.maxLength(80)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    role: ['EMPLOYEE' as Role, Validators.required],
    departmentId: [0],
    jobTitle: ['', Validators.maxLength(120)],
    phone: ['', Validators.pattern(PHONE_PATTERN)],
  });

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.serverErrors.set({});
    this.api
      .create(
        {
          email: v.email.trim(),
          firstName: v.firstName.trim(),
          lastName: v.lastName.trim(),
          jobTitle: v.jobTitle.trim() || null,
          phone: v.phone.trim() || null,
          departmentId: v.departmentId || null,
        },
        v.role,
      )
      .subscribe({
        next: (created) => this.ref.close(created),
        error: (error: unknown) => {
          this.saving.set(false);
          const problem = toApiProblem(error);
          if (problem.code === 'EMAIL_ALREADY_USED') {
            this.serverErrors.set({ email: problem.detail });
          } else if (problem.errors.length) {
            this.serverErrors.set(
              Object.fromEntries(problem.errors.map((e) => [e.field, e.message])),
            );
            this.error.set(problem.detail);
          } else {
            this.error.set(problem.detail);
          }
        },
      });
  }

  protected invalid(name: 'firstName' | 'lastName' | 'email' | 'phone'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }
}
