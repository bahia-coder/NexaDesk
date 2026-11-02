import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { KeyRound, LucideAngularModule, RefreshCw, TriangleAlert } from 'lucide-angular';

import { AuthService } from '../../core/auth/auth.service';
import { formatDateTime } from '../../core/format/time';
import { toApiProblem } from '../../core/http/api-error';
import { dataOf, loadState } from '../../core/http/load-state';
import { ROLE_LABELS } from '../../core/models/user.models';
import { ToastService } from '../../core/services/toast.service';
import { AvatarComponent } from '../../shared/ui/avatar.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { SpinnerComponent } from '../../shared/ui/spinner.component';
import { PHONE_PATTERN } from './users.models';
import { UsersService } from './users.service';

/** Avatar colours offered (dark enough for white initials). */
const AVATAR_COLORS = [
  '#4f46e5',
  '#7c3aed',
  '#db2777',
  '#dc2626',
  '#ea580c',
  '#0891b2',
  '#0d9488',
  '#16a34a',
  '#475569',
];

/**
 * The signed-in user's own profile. Identity, e-mail and role are managed by an administrator; the user changes
 * their job title, phone, avatar colour and password.
 */
@Component({
  selector: 'nx-profile-page',
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
  template: `
    <div class="page">
      <h1 class="page-title">Mon profil</h1>
      @if (state().status === 'error') {
        <div class="card">
          <nx-empty-state title="Profil indisponible" [icon]="icons.TriangleAlert">
            <button type="button" class="btn btn--secondary" (click)="retry()">
              <lucide-icon [img]="icons.RefreshCw" [size]="16" aria-hidden="true" /> Réessayer
            </button>
          </nx-empty-state>
        </div>
      } @else if (profile(); as p) {
        <div class="layout">
          <form class="card panel" [formGroup]="form" (ngSubmit)="save()" novalidate>
            <div class="identity">
              <nx-avatar
                [name]="p.fullName"
                [color]="form.controls.avatarColor.value"
                [size]="64"
              />
              <div>
                <h2 class="card__title">{{ p.fullName }}</h2>
                <p class="muted">{{ p.email }}</p>
              </div>
            </div>
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
                [attr.aria-invalid]="form.controls.phone.invalid && form.controls.phone.touched"
              />
              @if (form.controls.phone.invalid && form.controls.phone.touched) {
                <span class="field__error">Le numéro de téléphone n’est pas valide.</span>
              }
            </label>
            <fieldset class="field">
              <legend class="field__label">Couleur de l’avatar</legend>
              <div class="swatches">
                @for (color of colors; track color) {
                  <button
                    type="button"
                    class="swatch"
                    [style.background]="color"
                    [attr.aria-label]="'Couleur ' + color"
                    [attr.aria-pressed]="form.controls.avatarColor.value === color"
                    (click)="pick(color)"
                  ></button>
                }
              </div>
            </fieldset>
            @if (error(); as message) {
              <p class="field__error" role="alert">{{ message }}</p>
            }
            <div class="form-actions">
              <button type="submit" class="btn btn--primary" [disabled]="saving() || form.pristine">
                @if (saving()) {
                  <nx-spinner />
                }
                Enregistrer
              </button>
            </div>
          </form>

          <aside class="card panel">
            <dl class="facts">
              <dt>Rôle</dt>
              <dd>{{ roleLabels[p.role] }}</dd>
              <dt>Service</dt>
              <dd>{{ p.department ?? '—' }}</dd>
              <dt>Dernière connexion</dt>
              <dd>{{ exact(p.lastLoginAt) }}</dd>
            </dl>
            <p class="muted small">
              Nom, e-mail, service et rôle sont gérés par un administrateur.
            </p>
            <a class="btn btn--secondary" routerLink="/change-password">
              <lucide-icon [img]="icons.KeyRound" [size]="16" aria-hidden="true" /> Changer mon mot
              de passe
            </a>
          </aside>
        </div>
      } @else {
        <nx-skeleton height="280px" />
      }
    </div>
  `,
  styleUrl: './users.scss',
})
export class ProfilePage {
  private readonly api = inject(UsersService);
  private readonly auth = inject(AuthService);
  private readonly toasts = inject(ToastService);

  protected readonly icons = { KeyRound, RefreshCw, TriangleAlert };
  protected readonly roleLabels = ROLE_LABELS;
  protected readonly colors = AVATAR_COLORS;

  private readonly reload = signal(0);
  protected readonly state = loadState(this.reload, () => this.api.profile());
  protected readonly profile = computed(() => dataOf(this.state()));

  protected readonly form = inject(NonNullableFormBuilder).group({
    jobTitle: ['', Validators.maxLength(120)],
    phone: ['', Validators.pattern(PHONE_PATTERN)],
    avatarColor: ['#4f46e5', Validators.required],
  });
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const p = this.profile();
      if (p) {
        this.form.reset({
          jobTitle: p.jobTitle ?? '',
          phone: p.phone ?? '',
          avatarColor: p.avatarColor,
        });
      }
    });
  }

  protected pick(color: string): void {
    this.form.controls.avatarColor.setValue(color);
    this.form.markAsDirty();
  }

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.api
      .updateProfile({
        jobTitle: v.jobTitle.trim() || null,
        phone: v.phone.trim() || null,
        avatarColor: v.avatarColor,
      })
      .subscribe({
        next: (saved) => {
          this.saving.set(false);
          this.form.markAsPristine();
          this.auth.updateUser({ jobTitle: saved.jobTitle, avatarColor: saved.avatarColor });
          this.toasts.success('Profil enregistré.');
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.error.set(toApiProblem(error).detail);
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
