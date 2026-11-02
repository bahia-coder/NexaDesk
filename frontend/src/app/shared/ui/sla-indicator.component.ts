import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { LucideAngularModule, Pause } from 'lucide-angular';

import { slaDisplay } from '../../core/format/sla';
import { SlaView } from '../../core/models/ticket.models';
import { ClockService } from '../../core/services/clock.service';

/** Thin progress bar (consumed share of the resolution time) and the time left or overdue. */
@Component({
  selector: 'nx-sla-indicator',
  imports: [LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (display(); as d) {
      <div class="sla" [class]="'sla sla--' + d.tone" [attr.title]="d.detail">
        <div
          class="sla__bar"
          role="progressbar"
          aria-label="Temps de résolution consommé"
          [attr.aria-valuenow]="d.percent"
          aria-valuemin="0"
          aria-valuemax="100"
        >
          <span class="sla__fill" [style.width.%]="d.percent"></span>
        </div>
        <span class="sla__label">
          @if (d.paused) {
            <lucide-icon [img]="pauseIcon" [size]="12" aria-hidden="true" />
          }
          {{ d.label }}
        </span>
      </div>
    } @else {
      <span class="muted" title="Pas de SLA sur ce ticket">—</span>
    }
  `,
  styles: `
    .sla {
      display: flex;
      flex-direction: column;
      gap: 4px;
      min-width: 110px;
    }

    .sla__bar {
      height: 4px;
      border-radius: var(--radius-full);
      background: var(--color-border);
      overflow: hidden;
    }

    .sla__fill {
      display: block;
      height: 100%;
      border-radius: inherit;
      background: var(--sla-color);
      transition: width var(--duration-slow) var(--ease-standard);
    }

    .sla__label {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font: var(--text-xs);
      color: var(--sla-text);
    }

    .sla--success {
      --sla-color: var(--color-success);
      --sla-text: var(--color-success-text);
    }

    .sla--warning {
      --sla-color: var(--color-warning);
      --sla-text: var(--color-warning-text);
    }

    .sla--danger {
      --sla-color: var(--color-danger);
      --sla-text: var(--color-danger-text);
    }

    .sla--slate {
      --sla-color: var(--color-text-muted);
      --sla-text: var(--color-text-muted);
    }
  `,
})
export class SlaIndicatorComponent {
  private readonly clock = inject(ClockService);

  readonly sla = input.required<SlaView>();

  protected readonly pauseIcon = Pause;
  protected readonly display = computed(() => slaDisplay(this.sla(), this.clock.now()));
}
