import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import { Check, CircleAlert, KeyRound, LucideAngularModule, Minus } from 'lucide-angular';

import { AuthService } from '../../core/auth/auth.service';
import { passwordRules, strongPassword } from '../../core/auth/password-rules';
import { toApiProblem } from '../../core/http/api-error';
import { ToastService } from '../../core/services/toast.service';
import { SpinnerComponent } from '../../shared/ui/spinner.component';

function sameAsNew(control: AbstractControl): ValidationErrors | null {
  const group = control.parent;
  return group && control.value !== group.get('newPassword')?.value ? { mismatch: true } : null;
}

/**
 * Password change: forced after an admin created or reset the account (temporary password), or voluntary.
 * The server signs out every other session and returns fresh tokens for this one.
 */
@Component({
  selector: 'nx-change-password-page',
  imports: [ReactiveFormsModule, LucideAngularModule, SpinnerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './change-password.page.html',
  styleUrl: './auth-pages.scss',
})
export class ChangePasswordPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);

  protected readonly icons = { Check, CircleAlert, KeyRound, Minus };
  protected readonly forced = computed(() => this.auth.user()?.mustChangePassword ?? false);

  protected readonly form = inject(NonNullableFormBuilder).group({
    currentPassword: ['', [Validators.required, Validators.maxLength(128)]],
    newPassword: ['', [Validators.required, strongPassword]],
    confirmation: ['', [Validators.required, sameAsNew]],
  });

  private readonly newPassword = toSignal(this.form.controls.newPassword.valueChanges, {
    initialValue: '',
  });
  protected readonly rules = computed(() => passwordRules(this.newPassword()));

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    // The confirmation must be re-checked when the new password changes.
    this.form.controls.newPassword.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() =>
        this.form.controls.confirmation.updateValueAndValidity({ emitEvent: false }),
      );
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const { currentPassword, newPassword } = this.form.getRawValue();
    this.submitting.set(true);
    this.error.set(null);
    this.auth.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.toasts.success('Mot de passe modifié. Vos autres sessions ont été déconnectées.');
        void this.router.navigateByUrl('/');
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        const problem = toApiProblem(error);
        const fieldError = problem.errors.find((e) => e.field === 'newPassword');
        if (fieldError) {
          this.form.controls.newPassword.setErrors({ server: fieldError.message });
        } else {
          this.error.set(problem.detail);
        }
      },
    });
  }

  protected signOut(): void {
    this.auth.logout().subscribe();
  }

  protected invalid(name: 'currentPassword' | 'newPassword' | 'confirmation'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }
}
