import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Placeholder with the size of the content to come, so the page does not jump when data arrives.
 * `lines` > 1 stacks text lines, the last one shorter.
 */
@Component({
  selector: 'nx-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true', class: 'nx-skeleton' },
  template: `
    @for (line of rows(); track $index) {
      <span
        class="skeleton"
        [style.width]="$last && rows().length > 1 ? '60%' : width()"
        [style.height]="height()"
        [style.border-radius]="radius()"
      ></span>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
  `,
})
export class SkeletonComponent {
  readonly width = input('100%');
  readonly height = input('14px');
  readonly radius = input('var(--radius-sm)');
  readonly lines = input(1);

  protected readonly rows = computed(() => Array.from({ length: Math.max(1, this.lines()) }));
}
