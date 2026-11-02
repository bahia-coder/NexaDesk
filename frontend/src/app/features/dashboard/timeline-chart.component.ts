import {
  ChangeDetectionStrategy,
  Component,
  DOCUMENT,
  computed,
  inject,
  input,
} from '@angular/core';
import { ChartConfiguration } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';

import { ThemeService } from '../../core/services/theme.service';
import { bucketLabel } from './dashboard.format';
import { TimelinePoint } from './dashboard.models';

interface Palette {
  created: string;
  resolved: string;
  grid: string;
  axis: string;
  surface: string;
  text: string;
}

/**
 * Created vs resolved tickets over the period (one y-axis: both are ticket counts). Colours come from the theme
 * tokens and are re-read when the theme changes. A visually hidden table carries the same data for screen readers.
 */
@Component({
  selector: 'nx-timeline-chart',
  imports: [BaseChartDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="legend" aria-hidden="true">
      <span class="legend__item"><span class="swatch swatch--created"></span>Créés</span>
      <span class="legend__item"><span class="swatch swatch--resolved"></span>Résolus</span>
    </div>
    <div class="canvas">
      <canvas baseChart type="line" [data]="data()" [options]="options()"></canvas>
    </div>
    <table class="sr-only">
      <caption>
        Tickets créés et résolus par période
      </caption>
      <thead>
        <tr>
          <th scope="col">Période</th>
          <th scope="col">Créés</th>
          <th scope="col">Résolus</th>
        </tr>
      </thead>
      <tbody>
        @for (point of points(); track point.bucket) {
          <tr>
            <th scope="row">{{ label(point.bucket) }}</th>
            <td>{{ point.created }}</td>
            <td>{{ point.resolved }}</td>
          </tr>
        }
      </tbody>
    </table>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
    }

    .legend {
      display: flex;
      gap: var(--space-4);
      font: var(--text-small);
      color: var(--color-text-muted);
    }

    .legend__item {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
    }

    .swatch {
      width: 12px;
      height: 3px;
      border-radius: 2px;
    }

    .swatch--created {
      background: var(--viz-series-1);
    }

    .swatch--resolved {
      background: var(--viz-series-2);
    }

    .canvas {
      position: relative;
      height: 260px;
    }
  `,
})
export class TimelineChartComponent {
  private readonly theme = inject(ThemeService);
  private readonly document = inject(DOCUMENT);

  readonly points = input.required<TimelinePoint[]>();
  readonly granularity = input.required<'DAY' | 'WEEK' | 'MONTH'>();

  /** Re-read on every theme change (the signal is a dependency). */
  private readonly palette = computed<Palette>(() => {
    this.theme.theme();
    const style = getComputedStyle(this.document.documentElement);
    const token = (name: string) => style.getPropertyValue(name).trim();
    return {
      created: token('--viz-series-1'),
      resolved: token('--viz-series-2'),
      grid: token('--viz-grid'),
      axis: token('--viz-axis'),
      surface: token('--color-surface-raised'),
      text: token('--color-text'),
    };
  });

  protected readonly data = computed<ChartConfiguration<'line'>['data']>(() => {
    const p = this.palette();
    const points = this.points();
    const series = (label: string, color: string, values: number[]) => ({
      label,
      data: values,
      borderColor: color,
      backgroundColor: color,
      borderWidth: 2,
      tension: 0.3,
      pointRadius: points.length > 31 ? 0 : 3,
      pointHoverRadius: 5,
      pointBorderColor: p.surface,
      pointBorderWidth: 2,
    });
    return {
      labels: points.map((point) => this.label(point.bucket)),
      datasets: [
        series(
          'Créés',
          p.created,
          points.map((point) => point.created),
        ),
        series(
          'Résolus',
          p.resolved,
          points.map((point) => point.resolved),
        ),
      ],
    };
  });

  protected readonly options = computed<ChartConfiguration<'line'>['options']>(() => {
    const p = this.palette();
    return {
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      // Crosshair-style hover: one tooltip with both series for the hovered period.
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: p.surface,
          titleColor: p.text,
          bodyColor: p.text,
          borderColor: p.grid,
          borderWidth: 1,
          padding: 10,
          boxPadding: 4,
          usePointStyle: true,
        },
      },
      scales: {
        x: {
          grid: { display: false },
          border: { color: p.grid },
          ticks: { color: p.axis, maxRotation: 0, autoSkipPadding: 16 },
        },
        y: {
          beginAtZero: true,
          grid: { color: p.grid },
          border: { display: false },
          ticks: { color: p.axis, precision: 0 },
        },
      },
    };
  });

  protected label(bucket: string): string {
    return bucketLabel(bucket, this.granularity());
  }
}
