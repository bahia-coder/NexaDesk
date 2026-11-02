import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';

import { PRIORITY_LABELS } from '../../core/format/labels';
import { PriorityCode } from '../../core/models/ticket.models';
import { PENDING_LABELS } from './tickets.labels';
import { Category, PendingReason, Technician } from './tickets.models';
import { TicketsService } from './tickets.service';

export type ActionDialogMode = 'hold' | 'resolve' | 'assign' | 'reclassify';

export interface ActionDialogData {
  mode: ActionDialogMode;
  reference: string;
  categoryId: number;
  subcategoryId: number | null;
  priority: PriorityCode;
}

export type ActionDialogResult =
  | { mode: 'hold'; pendingReason: PendingReason; comment: string }
  | { mode: 'resolve'; resolution: string }
  | { mode: 'assign'; technicianId: number; comment: string }
  | {
      mode: 'reclassify';
      categoryId: number;
      subcategoryId: number | null;
      priority: PriorityCode;
      reason: string;
    };

const TITLES: Record<ActionDialogMode, string> = {
  hold: 'Mettre en attente',
  resolve: 'Résoudre le ticket',
  assign: 'Affecter le ticket',
  reclassify: 'Reclasser le ticket',
};

/** The workflow steps that need input: on-hold reason, resolution, technician, new classification. */
@Component({
  selector: 'nx-ticket-action-dialog',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="dialog" (ngSubmit)="submit()">
      <h2 id="nx-action-title" class="card__title">{{ title }} · {{ data.reference }}</h2>

      @switch (data.mode) {
        @case ('hold') {
          <label class="field">
            <span class="field__label">En attente de *</span>
            <select
              class="select"
              name="pending"
              [(ngModel)]="pendingReason"
              required
              cdkFocusInitial
            >
              @for (reason of pendingReasons; track reason) {
                <option [value]="reason">{{ pendingLabels[reason] }}</option>
              }
            </select>
          </label>
          <label class="field">
            <span class="field__label">Précision</span>
            <textarea
              class="textarea"
              name="comment"
              [(ngModel)]="comment"
              maxlength="1000"
            ></textarea>
            <span class="field__hint"
              >Le chrono du SLA de résolution est suspendu pendant l’attente.</span
            >
          </label>
        }
        @case ('resolve') {
          <label class="field">
            <span class="field__label">Solution appliquée</span>
            <textarea
              class="textarea"
              name="resolution"
              [(ngModel)]="resolution"
              maxlength="5000"
              rows="5"
              cdkFocusInitial
            ></textarea>
            <span class="field__hint">
              Visible par le demandeur. Laissée vide, la solution de la dernière intervention est
              reprise.
            </span>
          </label>
        }
        @case ('assign') {
          <label class="field">
            <span class="field__label">Technicien *</span>
            <select
              class="select"
              name="technician"
              [(ngModel)]="technicianId"
              required
              cdkFocusInitial
            >
              <option [ngValue]="null" disabled>Choisir…</option>
              @for (t of technicians(); track t.id) {
                <option [ngValue]="t.id">
                  {{ t.fullName }} · {{ t.openTickets }} en cours{{
                    isSpecialist(t) ? ' · spécialiste' : ''
                  }}
                </option>
              }
            </select>
            <span class="field__hint"
              >Les spécialistes de la catégorie sont proposés en premier.</span
            >
          </label>
          <label class="field">
            <span class="field__label">Commentaire</span>
            <textarea
              class="textarea"
              name="comment"
              [(ngModel)]="comment"
              maxlength="1000"
            ></textarea>
          </label>
        }
        @case ('reclassify') {
          <label class="field">
            <span class="field__label">Catégorie *</span>
            <select
              class="select"
              name="category"
              [ngModel]="categoryId()"
              (ngModelChange)="categoryId.set($event); subcategoryId = null"
              required
              cdkFocusInitial
            >
              @for (c of categories(); track c.id) {
                <option [ngValue]="c.id">{{ c.name }}</option>
              }
            </select>
          </label>
          <label class="field">
            <span class="field__label">Sous-catégorie</span>
            <select class="select" name="subcategory" [(ngModel)]="subcategoryId">
              <option [ngValue]="null">Aucune</option>
              @for (s of subcategories(); track s.id) {
                <option [ngValue]="s.id">{{ s.name }}</option>
              }
            </select>
          </label>
          <label class="field">
            <span class="field__label">Priorité *</span>
            <select class="select" name="priority" [(ngModel)]="priority" required>
              @for (p of priorities; track p) {
                <option [value]="p">{{ priorityLabels[p] }}</option>
              }
            </select>
          </label>
          <label class="field">
            <span class="field__label">Motif *</span>
            <textarea
              class="textarea"
              name="reason"
              [(ngModel)]="reason"
              maxlength="1000"
              required
            ></textarea>
            <span class="field__hint">Tracé dans l’historique du ticket.</span>
          </label>
        }
      }

      <div class="dialog__actions">
        <button type="button" class="btn btn--secondary" (click)="ref.close()">Annuler</button>
        <button type="submit" class="btn btn--primary" [disabled]="!valid()">Valider</button>
      </div>
    </form>
  `,
  styles: `
    .dialog {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
      width: 480px;
      max-width: 100%;
      padding: var(--space-6);
      border-radius: var(--radius-lg);
      background: var(--color-surface-raised);
      box-shadow: var(--shadow-lg);
    }

    .dialog__actions {
      display: flex;
      justify-content: flex-end;
      gap: var(--space-2);
    }
  `,
})
export class TicketActionDialogComponent {
  protected readonly data = inject<ActionDialogData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<ActionDialogResult>>(DialogRef);
  private readonly api = inject(TicketsService);

  protected readonly title = TITLES[this.data.mode];
  protected readonly pendingLabels = PENDING_LABELS;
  protected readonly pendingReasons = Object.keys(PENDING_LABELS) as PendingReason[];
  protected readonly priorityLabels = PRIORITY_LABELS;
  protected readonly priorities = Object.keys(PRIORITY_LABELS) as PriorityCode[];

  protected pendingReason: PendingReason = 'WAITING_USER';
  protected comment = '';
  protected resolution = '';
  protected technicianId: number | null = null;
  protected readonly categoryId = signal(this.data.categoryId);
  protected subcategoryId: number | null = this.data.subcategoryId;
  protected priority: PriorityCode = this.data.priority;
  protected reason = '';

  private readonly allTechnicians = toSignal(
    this.data.mode === 'assign' ? this.api.technicians().pipe(catchError(() => of([]))) : of([]),
    { initialValue: [] as Technician[] },
  );
  /** Specialists of the ticket's category first, then the least loaded. */
  protected readonly technicians = computed(() =>
    [...this.allTechnicians()].sort(
      (a, b) =>
        Number(this.isSpecialist(b)) - Number(this.isSpecialist(a)) ||
        a.openTickets - b.openTickets,
    ),
  );
  protected readonly categories = toSignal(
    this.data.mode === 'reclassify'
      ? this.api.categories().pipe(catchError(() => of([])))
      : of([] as Category[]),
    { initialValue: [] as Category[] },
  );
  protected readonly subcategories = computed(
    () =>
      this.categories()
        .find((c) => c.id === this.categoryId())
        ?.subcategories.filter((s) => s.active) ?? [],
  );

  protected isSpecialist(technician: Technician): boolean {
    return technician.specialties.some((s) => s.id === this.data.categoryId);
  }

  protected valid(): boolean {
    switch (this.data.mode) {
      case 'hold':
        return !!this.pendingReason;
      case 'resolve':
        return this.resolution.trim().length === 0 || this.resolution.trim().length >= 10;
      case 'assign':
        return this.technicianId !== null;
      case 'reclassify':
        return this.reason.trim().length > 0;
    }
  }

  protected submit(): void {
    if (!this.valid()) {
      return;
    }
    switch (this.data.mode) {
      case 'hold':
        this.ref.close({
          mode: 'hold',
          pendingReason: this.pendingReason,
          comment: this.comment.trim(),
        });
        break;
      case 'resolve':
        this.ref.close({ mode: 'resolve', resolution: this.resolution.trim() });
        break;
      case 'assign':
        this.ref.close({
          mode: 'assign',
          technicianId: this.technicianId!,
          comment: this.comment.trim(),
        });
        break;
      case 'reclassify':
        this.ref.close({
          mode: 'reclassify',
          categoryId: this.categoryId(),
          subcategoryId: this.subcategoryId,
          priority: this.priority,
          reason: this.reason.trim(),
        });
        break;
    }
  }
}
