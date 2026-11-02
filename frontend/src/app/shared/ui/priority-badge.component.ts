import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import {
  LucideAngularModule,
  LucideIconData,
  OctagonAlert,
  SignalHigh,
  SignalLow,
  SignalMedium,
} from 'lucide-angular';

import { PRIORITY_LABELS, PRIORITY_TONES } from '../../core/format/labels';
import { PriorityCode } from '../../core/models/ticket.models';

const ICONS: Record<PriorityCode, LucideIconData> = {
  LOW: SignalLow,
  MEDIUM: SignalMedium,
  HIGH: SignalHigh,
  CRITICAL: OctagonAlert,
};

/** Colour + icon + text: the priority never relies on colour alone. Critical gets a pulsing dot. */
@Component({
  selector: 'nx-priority-badge',
  imports: [LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span [class]="'badge badge--' + tone()">
      @if (code() === 'CRITICAL') {
        <span class="pulse" aria-hidden="true"></span>
      }
      <lucide-icon [img]="icon()" [size]="14" aria-hidden="true" />
      {{ text() }}
    </span>
  `,
  styles: `
    .pulse {
      width: 6px;
      height: 6px;
      border-radius: var(--radius-full);
      background: currentColor;
      animation: nx-pulse 1.6s ease-in-out infinite;
    }

    @keyframes nx-pulse {
      50% {
        opacity: 0.25;
      }
    }
  `,
})
export class PriorityBadgeComponent {
  readonly code = input.required<PriorityCode>();
  /** Admin-editable label sent by the API; the default French label otherwise. */
  readonly label = input<string>();

  protected readonly text = computed(() => this.label() ?? PRIORITY_LABELS[this.code()]);
  protected readonly tone = computed(() => PRIORITY_TONES[this.code()]);
  protected readonly icon = computed(() => ICONS[this.code()]);
}
