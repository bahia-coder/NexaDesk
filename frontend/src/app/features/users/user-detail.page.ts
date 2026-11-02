import { Dialog } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  linkedSignal,
  signal,
  WritableSignal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  ArrowLeft,
  KeyRound,
  LucideAngularModule,
  Power,
  RefreshCw,
  TriangleAlert,
} from 'lucide-angular';
import { Observable, catchError, of, switchMap } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { formatDateTime } from '../../core/format/time';
import { toApiProblem } from '../../core/http/api-error';
import { dataOf, loadState } from '../../core/http/load-state';
import { ROLE_LABELS, Role } from '../../core/models/user.models';
import { ClockService } from '../../core/services/clock.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { AvatarComponent } from '../../shared/ui/avatar.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { SpinnerComponent } from '../../shared/ui/spinner.component';
import { Category } from '../tickets/tickets.models';
import { TicketsService } from '../tickets/tickets.service';
import { TemporaryPasswordDialogComponent } from './temporary-password-dialog.component';
import {
  ACCOUNT_STATE_LABELS,
  Department,
  PHONE_PATTERN,
  TemporaryPassword,
  User,
  accountState,
} from './users.models';
import { UsersService } from './users.service';

/** One account (admins): identity, role, technician specialties, activation and password reset. */
@Component({
  selector: 'nx-user-detail-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    LucideAngularModule,
    AvatarComponent,
    EmptyStateComponent,
    SkeletonComponent,
    SpinnerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './user-detail.page.html',
  styleUrl: './users.scss',
})
export class UserDetailPage {
  private readonly api = inject(UsersService);
  private readonly tickets = inject(TicketsService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(Dialog);
  private readonly confirmService = inject(ConfirmService);
  private readonly toasts = inject(ToastService);
  private readonly clock = inject(ClockService);

  readonly id = input.required<string>();

  protected readonly icons = { ArrowLeft, KeyRound, Power, RefreshCw, TriangleAlert };
  protected readonly roles: Role[] = ['EMPLOYEE', 'TECHNICIAN', 'ADMIN'];
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly stateLabels = ACCOUNT_STATE_LABELS;

  private readonly userId = computed(() => Number(this.id()));
  private readonly reload = signal(0);
  protected readonly state = loadState(
    computed(() => ({ id: this.userId(), attempt: this.reload() })),
    ({ id }) => this.api.get(id),
  );
  /** Replaced by each action's response. */
  protected readonly user = linkedSignal<User | null>(() => dataOf(this.state()));
  /** An admin cannot change their own role nor disable their own account (enforced by the API too). */
  protected readonly self = computed(() => this.auth.user()?.id === this.userId());
  protected readonly accountState = computed(() => {
    const u = this.user();
    return u ? accountState(u, this.clock.now()) : null;
  });

  protected readonly departments = toSignal(
    this.api.departments().pipe(catchError(() => of([] as Department[]))),
    { initialValue: [] as Department[] },
  );
  protected readonly categories = toSignal(
    this.tickets.categories().pipe(catchError(() => of([] as Category[]))),
    { initialValue: [] as Category[] },
  );

  // ---- Identity ----------------------------------------------------------------------------------------
  protected readonly form = inject(NonNullableFormBuilder).group({
    firstName: ['', [Validators.required, Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.maxLength(80)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    departmentId: [0],
    jobTitle: ['', Validators.maxLength(120)],
    phone: ['', Validators.pattern(PHONE_PATTERN)],
  });
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);

  // ---- Role and specialties ----------------------------------------------------------------------------
  protected readonly role = linkedSignal<Role | null>(() => this.user()?.role ?? null);
  protected readonly savingRole = signal(false);
  private readonly specialtiesState = loadState(
    computed(() => ({ id: this.userId(), role: this.user()?.role })),
    ({ id, role }) =>
      role === 'TECHNICIAN' ? this.api.specialties(id).pipe(catchError(() => of([]))) : of([]),
  );
  protected readonly specialtyIds = linkedSignal<number[]>(
    () => dataOf(this.specialtiesState())?.map((c) => c.id) ?? [],
  );
  protected readonly savingSpecialties = signal(false);

  protected readonly busy = signal(false);

  constructor() {
    // Fill the identity form whenever a (new) version of the account arrives.
    effect(() => {
      const u = this.user();
      if (u) {
        this.form.reset({
          firstName: u.firstName,
          lastName: u.lastName,
          email: u.email,
          departmentId: u.departmentId ?? 0,
          jobTitle: u.jobTitle ?? '',
          phone: u.phone ?? '',
        });
      }
    });
  }

  protected saveIdentity(): void {
    const u = this.user();
    if (!u || this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.formError.set(null);
    this.api
      .update(u.id, {
        email: v.email.trim(),
        firstName: v.firstName.trim(),
        lastName: v.lastName.trim(),
        jobTitle: v.jobTitle.trim() || null,
        phone: v.phone.trim() || null,
        departmentId: v.departmentId || null,
      })
      .subscribe({
        next: (updated) => {
          this.saving.set(false);
          this.user.set(updated);
          this.toasts.success('Informations enregistrées.');
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.formError.set(toApiProblem(error).detail);
        },
      });
  }

  protected saveRole(): void {
    const u = this.user();
    const role = this.role();
    if (!u || !role || role === u.role) {
      return;
    }
    this.confirmed(
      {
        title: 'Changer le rôle',
        message: `${u.fullName} deviendra ${ROLE_LABELS[role].toLowerCase()}. Ses sessions en cours seront fermées.`,
        confirmLabel: 'Changer le rôle',
      },
      () => this.api.changeRole(u.id, role),
      'Rôle modifié.',
      this.savingRole,
    );
  }

  protected toggleSpecialty(categoryId: number, checked: boolean): void {
    this.specialtyIds.update((ids) =>
      checked ? [...ids, categoryId] : ids.filter((id) => id !== categoryId),
    );
  }

  protected saveSpecialties(): void {
    const u = this.user();
    if (!u) {
      return;
    }
    this.savingSpecialties.set(true);
    this.api.setSpecialties(u.id, this.specialtyIds()).subscribe({
      next: (saved) => {
        this.savingSpecialties.set(false);
        this.specialtyIds.set(saved.map((c) => c.id));
        this.toasts.success('Spécialités enregistrées.');
      },
      error: (error: unknown) => {
        this.savingSpecialties.set(false);
        this.toasts.error(toApiProblem(error).detail);
      },
    });
  }

  protected toggleActive(): void {
    const u = this.user();
    if (!u) {
      return;
    }
    if (u.active) {
      this.confirmed(
        {
          title: 'Désactiver le compte',
          message: `${u.fullName} ne pourra plus se connecter et ses sessions seront fermées. Ses tickets sont conservés.`,
          confirmLabel: 'Désactiver',
          tone: 'danger',
        },
        () => this.api.deactivate(u.id),
        'Compte désactivé.',
        this.busy,
      );
    } else {
      this.confirmed(
        {
          title: 'Réactiver le compte',
          message: `${u.fullName} pourra de nouveau se connecter (un éventuel verrouillage est levé).`,
          confirmLabel: 'Réactiver',
        },
        () => this.api.activate(u.id),
        'Compte réactivé.',
        this.busy,
      );
    }
  }

  protected resetPassword(): void {
    const u = this.user();
    if (!u) {
      return;
    }
    this.confirmService
      .confirm({
        title: 'Réinitialiser le mot de passe',
        message: `Un mot de passe temporaire sera généré pour ${u.fullName}, ses sessions seront fermées et il devra le changer à sa prochaine connexion.`,
        confirmLabel: 'Réinitialiser',
        tone: 'danger',
      })
      .pipe(switchMap((ok) => (ok ? this.api.resetPassword(u.id) : of(null))))
      .subscribe({
        next: (result: TemporaryPassword | null) => {
          if (result) {
            this.user.set(result.user);
            this.dialog.open(TemporaryPasswordDialogComponent, {
              data: result,
              ariaLabelledBy: 'nx-temp-password-title',
              panelClass: 'nx-dialog-panel',
              backdropClass: 'nx-backdrop',
              disableClose: true,
            });
          }
        },
        error: (error: unknown) => this.toasts.error(toApiProblem(error).detail),
      });
  }

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }

  protected exact(at: string | null): string {
    return at ? formatDateTime(at) : 'Jamais';
  }

  protected invalid(name: 'firstName' | 'lastName' | 'email' | 'phone'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }

  /** Confirmation, call, result in place, success toast; errors (last admin, own account…) as a toast. */
  private confirmed(
    options: { title: string; message: string; confirmLabel: string; tone?: 'primary' | 'danger' },
    call: () => Observable<User>,
    success: string,
    pending: WritableSignal<boolean>,
  ): void {
    this.confirmService
      .confirm(options)
      .pipe(
        switchMap((ok) => {
          if (!ok) {
            return of(null);
          }
          pending.set(true);
          return call();
        }),
      )
      .subscribe({
        next: (updated) => {
          pending.set(false);
          if (updated) {
            this.user.set(updated);
            this.toasts.success(success);
          } else {
            this.role.set(this.user()?.role ?? null);
          }
        },
        error: (error: unknown) => {
          pending.set(false);
          this.role.set(this.user()?.role ?? null);
          this.toasts.error(toApiProblem(error).detail);
        },
      });
  }
}
