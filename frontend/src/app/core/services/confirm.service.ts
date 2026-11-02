import { Dialog } from '@angular/cdk/dialog';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import {
  ConfirmDialogComponent,
  ConfirmOptions,
  ConfirmResult,
} from '../../shared/ui/confirm-dialog.component';

/**
 * Confirmation before an irreversible or consequential action. The CDK dialog traps the focus, closes on Escape
 * and gives the focus back to the button that opened it.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private readonly dialog = inject(Dialog);

  /** Emits the result once if confirmed, `null` if cancelled. */
  confirm(options: ConfirmOptions): Observable<ConfirmResult | null> {
    const ref = this.dialog.open<ConfirmResult, ConfirmOptions>(ConfirmDialogComponent, {
      data: options,
      ariaLabelledBy: 'nx-confirm-title',
      panelClass: 'nx-dialog-panel',
      backdropClass: 'nx-backdrop',
    });
    return ref.closed.pipe(map((result) => result ?? null));
  }
}
