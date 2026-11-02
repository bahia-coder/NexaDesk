import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Check, Copy, KeyRound, LucideAngularModule } from 'lucide-angular';

import { TemporaryPassword } from './users.models';

/**
 * Shows a temporary password once: the server does not keep it in clear, so it cannot be displayed again
 * (a new reset would generate another one). Closing needs an explicit click.
 */
@Component({
  selector: 'nx-temporary-password-dialog',
  imports: [LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dialog">
      <h2 id="nx-temp-password-title" class="card__title">
        <lucide-icon [img]="icons.KeyRound" [size]="18" aria-hidden="true" /> Mot de passe
        temporaire
      </h2>
      <p>
        Transmettez ce mot de passe à <strong>{{ data.user.fullName }}</strong> ({{
          data.user.email
        }}) par un canal sûr. Il devra le changer à sa première connexion.
      </p>
      <div class="secret">
        <code class="mono" aria-label="Mot de passe temporaire">{{ data.temporaryPassword }}</code>
        <button type="button" class="btn btn--secondary btn--sm" (click)="copy()">
          <lucide-icon [img]="copied() ? icons.Check : icons.Copy" [size]="16" aria-hidden="true" />
          {{ copied() ? 'Copié' : 'Copier' }}
        </button>
      </div>
      <p class="warning">Il ne sera plus affiché après la fermeture de cette fenêtre.</p>
      <div class="actions">
        <button type="button" class="btn btn--primary" (click)="ref.close()" cdkFocusInitial>
          J’ai transmis le mot de passe
        </button>
      </div>
    </div>
  `,
  styles: `
    .dialog {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
      width: 480px;
      max-width: 100%;
      padding: var(--space-6);
      border-radius: var(--radius-lg);
      background: var(--color-surface-raised);
      box-shadow: var(--shadow-lg);
    }

    h2 {
      display: flex;
      align-items: center;
      gap: var(--space-2);
    }

    .secret {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-3);
      padding: var(--space-3) var(--space-4);
      border: 1px dashed var(--color-border-strong);
      border-radius: var(--radius-md);
      background: var(--color-surface-hover);

      code {
        font-size: 1rem;
        letter-spacing: 0.04em;
        overflow-wrap: anywhere;
      }
    }

    .warning {
      font: var(--text-small);
      color: var(--color-warning-text);
    }

    .actions {
      display: flex;
      justify-content: flex-end;
    }
  `,
})
export class TemporaryPasswordDialogComponent {
  protected readonly data = inject<TemporaryPassword>(DIALOG_DATA);
  protected readonly ref = inject(DialogRef);
  protected readonly icons = { Check, Copy, KeyRound };
  protected readonly copied = signal(false);

  protected copy(): void {
    void navigator.clipboard
      ?.writeText(this.data.temporaryPassword)
      .then(() => this.copied.set(true));
  }
}
