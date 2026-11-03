import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { LucideAngularModule, Search } from 'lucide-angular';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  map,
  of,
  startWith,
  switchMap,
} from 'rxjs';

import { toApiProblem } from '../../core/http/api-error';
import { AvatarComponent } from '../../shared/ui/avatar.component';
import { SpinnerComponent } from '../../shared/ui/spinner.component';
import { User } from '../users/users.models';
import { UsersService } from '../users/users.service';
import { Equipment } from './equipment.models';
import { EquipmentService } from './equipment.service';

const RESULTS = 8;

/** Hands a device to an active user (admins); the previous holder's assignment is closed by the API. */
@Component({
  selector: 'nx-assign-dialog',
  imports: [LucideAngularModule, AvatarComponent, SpinnerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dialog">
      <h2 id="nx-assign-title" class="card__title">Affecter {{ data.inventoryCode }}</h2>
      <p class="muted">
        {{ data.name }}
        @if (data.assignedUser; as holder) {
          · actuellement chez {{ holder.fullName }}
        }
      </p>
      <label class="search">
        <lucide-icon [img]="icons.Search" [size]="16" aria-hidden="true" />
        <span class="sr-only">Rechercher un utilisateur</span>
        <input
          class="input"
          type="search"
          placeholder="Nom, prénom, e-mail…"
          cdkFocusInitial
          (input)="query.set($any($event.target).value)"
        />
      </label>

      @if (results(); as list) {
        @if (list.length === 0) {
          <p class="muted state">Aucun compte actif ne correspond.</p>
        } @else {
          <ul class="results" aria-label="Utilisateurs">
            @for (u of list; track u.id) {
              <li>
                <button
                  type="button"
                  class="result"
                  [disabled]="saving() !== null || u.id === data.assignedUser?.id"
                  (click)="assign(u)"
                >
                  <nx-avatar [name]="u.fullName" [color]="u.avatarColor" [size]="32" />
                  <span class="result__text">
                    <span>{{ u.fullName }}</span>
                    <span class="muted small"
                      >{{ u.email }}{{ u.departmentName ? ' · ' + u.departmentName : '' }}</span
                    >
                  </span>
                  @if (saving() === u.id) {
                    <nx-spinner />
                  } @else if (u.id === data.assignedUser?.id) {
                    <span class="muted small">Détenteur actuel</span>
                  }
                </button>
              </li>
            }
          </ul>
        }
      } @else {
        <p class="muted state">Recherche…</p>
      }

      @if (error(); as message) {
        <p class="field__error" role="alert">{{ message }}</p>
      }
      <div class="actions">
        <button type="button" class="btn btn--secondary" (click)="ref.close()">Annuler</button>
      </div>
    </div>
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

    .search {
      position: relative;

      lucide-icon {
        position: absolute;
        top: 50%;
        left: var(--space-3);
        transform: translateY(-50%);
        color: var(--color-text-muted);
      }

      .input {
        padding-left: 34px;
      }
    }

    .results {
      display: flex;
      flex-direction: column;
      max-height: 360px;
      margin: 0;
      padding: 0;
      overflow-y: auto;
      list-style: none;
    }

    .result {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      width: 100%;
      padding: var(--space-2) var(--space-3);
      border: 0;
      border-radius: var(--radius-sm);
      background: transparent;
      text-align: left;
      cursor: pointer;

      &:hover:not(:disabled) {
        background: var(--color-surface-hover);
      }

      &:disabled {
        cursor: default;
      }
    }

    .result__text {
      display: flex;
      flex: 1;
      flex-direction: column;
      min-width: 0;
    }

    .small {
      font: var(--text-xs);
    }

    .state {
      padding: var(--space-4) 0;
      text-align: center;
    }

    .actions {
      display: flex;
      justify-content: flex-end;
    }
  `,
})
export class AssignDialogComponent {
  protected readonly data = inject<Equipment>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<Equipment>>(DialogRef);
  private readonly users = inject(UsersService);
  private readonly api = inject(EquipmentService);

  protected readonly icons = { Search };
  protected readonly query = signal('');
  protected readonly saving = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);

  /** Active accounts matching the search; null while a search is in flight. */
  protected readonly results = toSignal(
    toObservable(this.query).pipe(
      map((q) => q.trim()),
      debounceTime(250),
      distinctUntilChanged(),
      switchMap((search) =>
        this.users
          .list({ search: search || undefined, active: true }, 0, RESULTS, 'lastName,asc')
          .pipe(
            map((page) => page.content as User[] | null),
            catchError(() => of([] as User[])),
            startWith(null),
          ),
      ),
    ),
    { initialValue: null },
  );

  protected assign(user: User): void {
    this.saving.set(user.id);
    this.error.set(null);
    this.api.assign(this.data.id, user.id).subscribe({
      next: (updated) => this.ref.close(updated),
      error: (error: unknown) => {
        this.saving.set(null);
        this.error.set(toApiProblem(error).detail);
      },
    });
  }
}
