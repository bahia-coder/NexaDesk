import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export interface BarListItem {
  label: string;
  value: number;
  /** Identity colour of the item (e.g. the priority's colour); the first series colour otherwise. */
  color?: string | null;
}

/**
 * Horizontal bars for a breakdown (by priority, category, status). Length encodes the count, the value is written
 * next to each bar in text colour, so nothing relies on the bar colour alone. Plain HTML: themeable and readable
 * by screen readers as a list.
 */
@Component({
  selector: 'nx-bar-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (rows().length === 0) {
      <p class="muted empty">{{ emptyText() }}</p>
    } @else {
      <ul class="bars" [attr.aria-label]="label()">
        @for (row of rows(); track row.label) {
          <li class="bar">
            <span class="bar__label" [attr.title]="row.label">{{ row.label }}</span>
            <span class="bar__track" aria-hidden="true">
              <span
                class="bar__fill"
                [style.width.%]="row.width"
                [style.background]="row.color ?? 'var(--viz-series-1)'"
              ></span>
            </span>
            <span class="bar__value">
              {{ row.value }}
              <span class="sr-only">({{ row.share }} %)</span>
            </span>
          </li>
        }
      </ul>
    }
  `,
  styles: `
    .bars {
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .bar {
      display: grid;
      grid-template-columns: minmax(80px, 34%) minmax(0, 1fr) 40px;
      align-items: center;
      gap: var(--space-3);
      font: var(--text-small);
    }

    .bar__label {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: var(--color-text);
    }

    .bar__track {
      height: 8px;
    }

    .bar__fill {
      display: block;
      min-width: 4px;
      height: 100%;
      border-radius: 0 4px 4px 0;
      transition: width var(--duration-slow) var(--ease-standard);
    }

    .bar__value {
      text-align: right;
      font-weight: 600;
      color: var(--color-text);
    }

    .empty {
      padding: var(--space-6) 0;
      text-align: center;
    }
  `,
})
export class BarListComponent {
  readonly items = input.required<BarListItem[]>();
  /** Accessible name of the list ("Tickets par priorité"). */
  readonly label = input.required<string>();
  readonly emptyText = input('Aucune donnée sur la période.');
  /** Hides zero rows (e.g. statuses without tickets). */
  readonly hideZero = input(false);

  protected readonly rows = computed(() => {
    const items = this.items().filter((item) => !this.hideZero() || item.value > 0);
    const max = Math.max(0, ...items.map((item) => item.value));
    const total = items.reduce((sum, item) => sum + item.value, 0);
    return items.map((item) => ({
      ...item,
      width: max === 0 ? 0 : (item.value / max) * 100,
      share: total === 0 ? 0 : Math.round((item.value / total) * 100),
    }));
  });
}
