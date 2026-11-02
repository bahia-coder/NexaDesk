import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { CircleAlert, Eye, EyeOff, LogIn, LucideAngularModule } from 'lucide-angular';

import { DemoAccount } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { safeReturnUrl } from '../../core/auth/auth.guards';
import { toApiProblem } from '../../core/http/api-error';
import { ROLE_LABELS } from '../../core/models/user.models';
import { SpinnerComponent } from '../../shared/ui/spinner.component';

@Component({
  selector: 'nx-login-page',
  imports: [ReactiveFormsModule, LucideAngularModule, SpinnerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.page.html',
  styleUrl: './auth-pages.scss',
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Query parameters (component input binding). */
  readonly returnUrl = input<string>();
  readonly expired = input<string>();

  protected readonly icons = { CircleAlert, Eye, EyeOff, LogIn };
  protected readonly roleLabels = ROLE_LABELS;

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });

  protected readonly submitting = signal(false);
  protected readonly showPassword = signal(false);
  /** Server-side refusal (wrong credentials, locked or disabled account), shown above the form. */
  protected readonly error = signal<string | null>(null);
  protected readonly demoAccounts = toSignal(this.auth.demoAccounts(), {
    initialValue: [] as DemoAccount[],
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const { email, password } = this.form.getRawValue();
    this.submitting.set(true);
    this.error.set(null);
    this.auth.login(email.trim(), password).subscribe({
      next: (user) => {
        void this.router.navigateByUrl(
          user.mustChangePassword ? '/change-password' : safeReturnUrl(this.returnUrl()),
        );
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.error.set(toApiProblem(error).detail);
      },
    });
  }

  protected signInAs(account: DemoAccount): void {
    this.form.setValue({ email: account.email, password: account.password });
    this.submit();
  }

  protected invalid(name: 'email' | 'password'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }
}
