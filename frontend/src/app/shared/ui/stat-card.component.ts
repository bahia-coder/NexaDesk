import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import {
  LucideAngularModule,
  LucideIconData,
  Minus,
  TrendingDown,
  TrendingUp,
} from 'lucide-angular';

import { Tone } from '../../core/format/labels';

/**
 * KPI card: icon, value, label and the trend against the previous period (computed by the API).
 * {@link goodWhen} says which direction is good news ("overdue tickets ↑" is bad), which drives the colour.
 */
@Component({
  selector: 'nx-stat-card',
  imports: [LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="stat card" [attr.aria-busy]="loading()">
      <div class="stat__head">
        <span [class]="'stat__icon badge--' + tone()">
          <lucide-icon [img]="icon()" [size]="18" aria-hidden="true" />
        </span>
        <span class="stat__label">{{ label() }}</span>
      </div>
      @if (loading()) {
        <span class="skeleton" style="width: 96px; height: 36px"></span>
        <span class="skeleton" style="width: 140px; height: 16px"></span>
      } @else {
        <span class="stat__value">{{ value() }}</span>
        @if (trend(); as t) {
          <span [class]="'stat__trend stat__trend--' + t.quality">
            <lucide-icon [img]="t.icon" [size]="14" aria-hidden="true" />
            {{ t.text }}
            <span class="muted">vs période précédente</span>
          </span>
        } @else if (hint()) {
          <span class="stat__trend muted">{{ hint() }}</span>
        }
      }
    </article>
  `,
  styles: `
    .stat {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      min-width: 0;
    }

    .stat__head {
      display: flex;
      align-items: center;
      gap: var(--space-3);
    }

    .stat__icon {
      display: grid;
      place-items: center;
      width: 36px;
      height: 36px;
      border-radius: var(--radius-md);
      flex-shrink: 0;
    }

    .stat__label {
      font: 500 0.8125rem/1.125rem var(--font-sans);
      color: var(--color-text-muted);
    }

    .stat__value {
      font: var(--text-display);
      letter-spacing: -0.02em;
    }

    .stat__trend {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font: var(--text-small);
    }

    .stat__trend--good {
      color: var(--color-success-text);
    }

    .stat__trend--bad {
      color: var(--color-danger-text);
    }

    .stat__trend--neutral {
      color: var(--color-text-muted);
    }
  `,
})
export class StatCardComponent {
  readonly label = input.required<string>();
  readonly value = input.required<string | number>();
  readonly icon = input.required<LucideIconData>();
  readonly tone = input<Tone>('indigo');
  /** Relative change in %, or in points when {@link changeUnit} is "pts"; null when not comparable. */
  readonly change = input<number | null>(null);
  readonly changeUnit = input<'%' | 'pts'>('%');
  readonly goodWhen = input<'up' | 'down'>('up');
  /** Shown instead of the trend when there is none. */
  readonly hint = input<string>();
  readonly loading = input(false);

  protected readonly trend = computed(() => {
    const change = this.change();
    if (change === null) {
      return null;
    }
    const rounded = Math.round(change * 10) / 10;
    const text = `${rounded > 0 ? '+' : ''}${rounded.toLocaleString('fr-FR')} ${this.changeUnit()}`;
    if (rounded === 0) {
      return { icon: Minus, text, quality: 'neutral' as const };
    }
    const up = rounded > 0;
    const good = up === (this.goodWhen() === 'up');
    return {
      icon: up ? TrendingUp : TrendingDown,
      text,
      quality: good ? ('good' as const) : ('bad' as const),
    };
  });
}
