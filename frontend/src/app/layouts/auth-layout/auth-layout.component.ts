import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { BellRing, Clock, Hexagon, LucideAngularModule, Moon, Sparkles, Sun } from 'lucide-angular';

import { ThemeService } from '../../core/services/theme.service';

/** Public pages (sign-in): the form on one side, a product panel on the other (hidden on small screens). */
@Component({
  selector: 'nx-auth-layout',
  imports: [RouterOutlet, LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="auth">
      <main class="auth__form">
        <div class="auth__top">
          <span class="auth__brand">
            <span class="auth__logo">
              <lucide-icon [img]="icons.Hexagon" [size]="20" aria-hidden="true" />
            </span>
            NexaDesk
          </span>
          <button
            type="button"
            class="btn btn--ghost btn--icon"
            [attr.aria-label]="theme.isDark() ? 'Passer au thème clair' : 'Passer au thème sombre'"
            (click)="theme.toggle()"
          >
            <lucide-icon
              [img]="theme.isDark() ? icons.Sun : icons.Moon"
              [size]="18"
              aria-hidden="true"
            />
          </button>
        </div>
        <div class="auth__content">
          <router-outlet />
        </div>
      </main>

      <aside class="auth__visual" aria-hidden="true">
        <div class="auth__glow"></div>
        <div class="auth__pitch">
          <p class="auth__tagline">Résolvez plus vite.<br />Mesurez ce qui compte.</p>
          <ul>
            <li>
              <lucide-icon [img]="icons.Clock" [size]="18" />
              SLA suivis en heures ouvrées, en temps réel
            </li>
            <li>
              <lucide-icon [img]="icons.BellRing" [size]="18" />
              Notifications instantanées
            </li>
            <li>
              <lucide-icon [img]="icons.Sparkles" [size]="18" />
              Suggestions intelligentes et base de connaissances
            </li>
          </ul>
        </div>
      </aside>
    </div>
  `,
  styles: `
    .auth {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
      min-height: 100vh;

      @media (width < 1024px) {
        grid-template-columns: minmax(0, 1fr);
      }
    }

    .auth__form {
      display: flex;
      flex-direction: column;
      padding: var(--space-6);
    }

    .auth__top {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .auth__brand {
      display: inline-flex;
      align-items: center;
      gap: var(--space-3);
      font: 600 1rem/1 var(--font-sans);
    }

    .auth__logo {
      display: grid;
      place-items: center;
      width: 32px;
      height: 32px;
      border-radius: var(--radius-md);
      background: linear-gradient(135deg, #6366f1, #4338ca);
      color: #ffffff;
    }

    .auth__content {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: var(--space-8) 0;
    }

    .auth__visual {
      position: relative;
      display: flex;
      align-items: flex-end;
      padding: var(--space-12);
      overflow: hidden;
      background: linear-gradient(160deg, #312e81 0%, #1e1b4b 45%, #0f172a 100%);
      color: #e0e7ff;

      @media (width < 1024px) {
        display: none;
      }
    }

    .auth__glow {
      position: absolute;
      inset: -20% -10% auto auto;
      width: 70%;
      aspect-ratio: 1;
      border-radius: 50%;
      background: radial-gradient(circle, rgb(129 140 248 / 0.45), transparent 65%);
      filter: blur(20px);
    }

    .auth__pitch {
      position: relative;
      display: flex;
      flex-direction: column;
      gap: var(--space-8);
    }

    .auth__tagline {
      font: 600 2rem/2.5rem var(--font-sans);
      letter-spacing: -0.02em;
      color: #ffffff;
    }

    ul {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    li {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      font: 400 0.9375rem/1.5rem var(--font-sans);
    }
  `,
})
export class AuthLayoutComponent {
  protected readonly theme = inject(ThemeService);
  protected readonly icons = { BellRing, Clock, Hexagon, Moon, Sparkles, Sun };
}
