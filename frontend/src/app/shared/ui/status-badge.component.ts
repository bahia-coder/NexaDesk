import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { STATUS_LABELS, STATUS_TONES } from '../../core/format/labels';
import { TicketStatus } from '../../core/models/ticket.models';

@Component({
  selector: 'nx-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span [class]="'badge badge--' + tone()">{{ label() }}</span>`,
})
export class StatusBadgeComponent {
  readonly status = input.required<TicketStatus>();

  protected readonly label = computed(() => STATUS_LABELS[this.status()]);
  protected readonly tone = computed(() => STATUS_TONES[this.status()]);
}
