import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import {
  CircleAlert,
  CircleCheck,
  Info,
  LucideAngularModule,
  TriangleAlert,
  X,
} from 'lucide-angular';

import { ToastKind, ToastService } from '../../core/services/toast.service';

/** Rendered once by the root component. Errors are announced assertively, the rest politely. */
@Component({
  selector: 'nx-toast-container',
  imports: [LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toasts">
      @for (toast of service.toasts(); track toast.id) {
        <div
          [class]="'toast toast--' + toast.kind"
          [attr.role]="toast.kind === 'error' ? 'alert' : 'status'"
          [attr.aria-live]="toast.kind === 'error' ? 'assertive' : 'polite'"
        >
          <lucide-icon
            class="toast__icon"
            [img]="icons[toast.kind]"
            [size]="18"
            aria-hidden="true"
          />
          <div class="toast__body">
            <p>{{ toast.message }}</p>
            @if (toast.detail) {
              <p class="toast__detail">{{ toast.detail }}</p>
            }
          </div>
          <button
            type="button"
            class="btn btn--ghost btn--icon btn--sm"
            aria-label="Fermer"
            (click)="service.dismiss(toast.id)"
          >
            <lucide-icon [img]="closeIcon" [size]="16" aria-hidden="true" />
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    .toasts {
      position: fixed;
      right: var(--space-4);
      bottom: var(--space-4);
      z-index: 1100;
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      width: min(380px, calc(100vw - 32px));
    }

    .toast {
      display: flex;
      align-items: flex-start;
      gap: var(--space-3);
      padding: var(--space-3) var(--space-2) var(--space-3) var(--space-4);
      border: 1px solid var(--color-border);
      border-left: 3px solid var(--toast-color);
      border-radius: var(--radius-md);
      background: var(--color-surface-raised);
      box-shadow: var(--shadow-md);
      animation: nx-toast-in var(--duration-base) var(--ease-standard);
    }

    .toast__icon {
      margin-top: 1px;
      color: var(--toast-color);
    }

    .toast__body {
      flex: 1;
      min-width: 0;
    }

    .toast__detail {
      font: var(--text-small);
      color: var(--color-text-muted);
    }

    .toast--success {
      --toast-color: var(--color-success);
    }

    .toast--error {
      --toast-color: var(--color-danger);
    }

    .toast--warning {
      --toast-color: var(--color-warning);
    }

    .toast--info {
      --toast-color: var(--color-info);
    }

    @keyframes nx-toast-in {
      from {
        opacity: 0;
        transform: translateY(8px);
      }
    }
  `,
})
export class ToastContainerComponent {
  protected readonly service = inject(ToastService);
  protected readonly closeIcon = X;
  protected readonly icons: Record<ToastKind, typeof Info> = {
    success: CircleCheck,
    error: CircleAlert,
    warning: TriangleAlert,
    info: Info,
  };
}
