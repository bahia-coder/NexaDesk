import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { initials } from '../../core/format/time';

/** Initials on the colour the API assigned to the user (no photo upload in this application). */
@Component({
  selector: 'nx-avatar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      class="avatar"
      [style.background]="color()"
      [style.width.px]="size()"
      [style.height.px]="size()"
      [style.font-size.px]="size() * 0.4"
      [attr.title]="name()"
      role="img"
      [attr.aria-label]="name()"
    >
      {{ letters() }}
    </span>
  `,
  styles: `
    .avatar {
      display: inline-grid;
      place-items: center;
      flex-shrink: 0;
      border-radius: var(--radius-full);
      color: #ffffff;
      font-weight: 600;
      line-height: 1;
      user-select: none;
    }
  `,
})
export class AvatarComponent {
  readonly name = input.required<string>();
  readonly color = input('#64748b');
  readonly size = input(32);

  protected readonly letters = computed(() => initials(this.name()));
}
