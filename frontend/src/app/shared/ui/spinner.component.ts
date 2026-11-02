import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { LoaderCircle, LucideAngularModule } from 'lucide-angular';

/** Inline loading indicator, e.g. inside a button while its request runs. */
@Component({
  selector: 'nx-spinner',
  imports: [LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<lucide-icon class="spin" [img]="icon" [size]="size()" aria-hidden="true" />`,
  styles: `
    :host {
      display: inline-flex;
    }

    .spin {
      animation: nx-spin 0.8s linear infinite;
    }

    @keyframes nx-spin {
      to {
        transform: rotate(360deg);
      }
    }
  `,
})
export class SpinnerComponent {
  readonly size = input(16);
  protected readonly icon = LoaderCircle;
}
