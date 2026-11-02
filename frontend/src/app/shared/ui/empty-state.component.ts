import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Inbox, LucideAngularModule, LucideIconData } from 'lucide-angular';

/** Empty list: what is missing, why, and (projected content) the useful next action. */
@Component({
  selector: 'nx-empty-state',
  imports: [LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="empty">
      <span class="empty__icon">
        <lucide-icon [img]="icon()" [size]="28" [strokeWidth]="1.5" aria-hidden="true" />
      </span>
      <h3 class="empty__title">{{ title() }}</h3>
      @if (description()) {
        <p class="empty__text">{{ description() }}</p>
      }
      <div class="empty__actions"><ng-content /></div>
    </div>
  `,
  styles: `
    .empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--space-2);
      padding: var(--space-10) var(--space-6);
      text-align: center;
    }

    .empty__icon {
      display: grid;
      place-items: center;
      width: 56px;
      height: 56px;
      margin-bottom: var(--space-2);
      border-radius: var(--radius-full);
      background: var(--color-primary-subtle);
      color: var(--color-primary);
    }

    .empty__title {
      font: var(--text-h2);
    }

    .empty__text {
      max-width: 420px;
      color: var(--color-text-muted);
    }

    .empty__actions:not(:empty) {
      margin-top: var(--space-3);
    }
  `,
})
export class EmptyStateComponent {
  readonly title = input.required<string>();
  readonly description = input<string>();
  readonly icon = input<LucideIconData>(Inbox);
}
