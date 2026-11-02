import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

export interface ConfirmOptions {
  title: string;
  /** What will happen, in plain words. */
  message: string;
  confirmLabel: string;
  tone?: 'primary' | 'danger';
  /** Asks for a reason (rejection, reopening…), sent with the action. */
  reason?: { label: string; required: boolean; maxLength: number };
}

/** {@code reason} is the trimmed text, or undefined when no reason was asked. */
export interface ConfirmResult {
  reason?: string;
}

@Component({
  selector: 'nx-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form class="dialog" (submit)="$event.preventDefault(); confirm()">
      <h2 id="nx-confirm-title" class="card__title">{{ data.title }}</h2>
      <p class="muted">{{ data.message }}</p>
      @if (data.reason; as r) {
        <label class="field">
          <span class="field__label"
            >{{ r.label }}
            @if (r.required) {
              <span aria-hidden="true">*</span>
            }
          </span>
          <textarea
            class="textarea"
            [attr.maxlength]="r.maxLength"
            [attr.aria-required]="r.required"
            [value]="reason()"
            (input)="reason.set($any($event.target).value)"
            cdkFocusInitial
          ></textarea>
          <span class="field__hint">{{ reason().length }} / {{ r.maxLength }}</span>
        </label>
      }
      <div class="dialog__actions">
        <button type="button" class="btn btn--secondary" (click)="ref.close()">Annuler</button>
        <button
          type="submit"
          [class]="'btn btn--' + (data.tone ?? 'primary')"
          [disabled]="!valid()"
        >
          {{ data.confirmLabel }}
        </button>
      </div>
    </form>
  `,
  styles: `
    .dialog {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
      width: 440px;
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
export class ConfirmDialogComponent {
  protected readonly data = inject<ConfirmOptions>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<ConfirmResult>>(DialogRef);
  protected readonly reason = signal('');

  protected readonly valid = computed(
    () => !this.data.reason?.required || this.reason().trim().length > 0,
  );

  protected confirm(): void {
    if (!this.valid()) {
      return;
    }
    this.ref.close(this.data.reason ? { reason: this.reason().trim() } : {});
  }
}
