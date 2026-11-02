import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Cpu, LucideAngularModule, Sparkles } from 'lucide-angular';

import { AiSource } from '../../core/ai/ai.models';

/** Says honestly where a suggestion comes from: a connected model, or local rules without any model. */
@Component({
  selector: 'nx-ai-source-badge',
  imports: [LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (source() === 'LLM') {
      <span class="badge badge--violet" [attr.title]="disclaimer()">
        <lucide-icon [img]="icons.Sparkles" [size]="12" aria-hidden="true" /> Modèle IA
      </span>
    } @else {
      <span class="badge badge--slate" [attr.title]="disclaimer()">
        <lucide-icon [img]="icons.Cpu" [size]="12" aria-hidden="true" /> Mode démo — heuristique
      </span>
    }
  `,
})
export class AiSourceBadgeComponent {
  readonly source = input.required<AiSource>();
  readonly disclaimer = input('');
  protected readonly icons = { Cpu, Sparkles };
}
