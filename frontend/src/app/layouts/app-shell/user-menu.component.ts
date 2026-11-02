import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ChevronDown, KeyRound, LogOut, LucideAngularModule, UserRound } from 'lucide-angular';

import { AuthService } from '../../core/auth/auth.service';
import { ROLE_LABELS } from '../../core/models/user.models';
import { AvatarComponent } from '../../shared/ui/avatar.component';

/** Header account menu. The CDK menu handles arrow keys, Escape and focus return. */
@Component({
  selector: 'nx-user-menu',
  imports: [CdkMenuTrigger, CdkMenu, CdkMenuItem, LucideAngularModule, AvatarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (auth.user(); as user) {
      <button
        type="button"
        class="trigger"
        [cdkMenuTriggerFor]="menu"
        [attr.aria-label]="'Compte de ' + user.fullName"
      >
        <nx-avatar [name]="user.fullName" [color]="user.avatarColor" [size]="32" />
        <span class="trigger__text">
          <span class="trigger__name">{{ user.fullName }}</span>
          <span class="trigger__role">{{ roleLabels[user.role] }}</span>
        </span>
        <lucide-icon [img]="icons.ChevronDown" [size]="16" aria-hidden="true" />
      </button>

      <ng-template #menu>
        <div class="menu" cdkMenu>
          <div class="menu__identity">
            <span class="menu__name">{{ user.fullName }}</span>
            <span class="muted">{{ user.email }}</span>
          </div>
          <button type="button" class="menu__item" cdkMenuItem (cdkMenuItemTriggered)="profile()">
            <lucide-icon [img]="icons.UserRound" [size]="16" aria-hidden="true" />
            Mon profil
          </button>
          <button
            type="button"
            class="menu__item"
            cdkMenuItem
            (cdkMenuItemTriggered)="changePassword()"
          >
            <lucide-icon [img]="icons.KeyRound" [size]="16" aria-hidden="true" />
            Changer mon mot de passe
          </button>
          <button type="button" class="menu__item" cdkMenuItem (cdkMenuItemTriggered)="logout()">
            <lucide-icon [img]="icons.LogOut" [size]="16" aria-hidden="true" />
            Se déconnecter
          </button>
        </div>
      </ng-template>
    }
  `,
  styles: `
    .trigger {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      height: 40px;
      padding: 0 var(--space-2) 0 var(--space-1);
      border: 0;
      border-radius: var(--radius-full);
      background: transparent;
      color: var(--color-text-muted);
      cursor: pointer;

      &:hover,
      &[aria-expanded='true'] {
        background: var(--color-surface-hover);
      }
    }

    .trigger__text {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      line-height: 1.1;

      @media (width < 640px) {
        display: none;
      }
    }

    .trigger__name {
      font: 500 0.8125rem/1.1 var(--font-sans);
      color: var(--color-text);
    }

    .trigger__role {
      font: var(--text-xs);
    }

    .menu {
      display: flex;
      flex-direction: column;
      min-width: 240px;
      margin-top: var(--space-1);
      padding: var(--space-1);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      background: var(--color-surface-raised);
      box-shadow: var(--shadow-md);
    }

    .menu__identity {
      display: flex;
      flex-direction: column;
      padding: var(--space-2) var(--space-3) var(--space-3);
      margin-bottom: var(--space-1);
      border-bottom: 1px solid var(--color-border);
      font: var(--text-small);
    }

    .menu__name {
      font-weight: 600;
    }

    .menu__item {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      height: 36px;
      padding: 0 var(--space-3);
      border: 0;
      border-radius: var(--radius-sm);
      background: transparent;
      text-align: left;
      cursor: pointer;

      &:hover,
      &:focus-visible {
        background: var(--color-surface-hover);
        outline: none;
      }
    }
  `,
})
export class UserMenuComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly roleLabels = ROLE_LABELS;
  protected readonly icons = { ChevronDown, KeyRound, LogOut, UserRound };

  protected profile(): void {
    void this.router.navigateByUrl('/profile');
  }

  protected changePassword(): void {
    void this.router.navigateByUrl('/change-password');
  }

  protected logout(): void {
    this.auth.logout().subscribe();
  }
}
