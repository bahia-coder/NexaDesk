import { Dialog } from '@angular/cdk/dialog';
import { Injectable, inject } from '@angular/core';
import { EMPTY, Observable, catchError, switchMap, tap } from 'rxjs';

import { toApiProblem } from '../../core/http/api-error';
import { PriorityCode } from '../../core/models/ticket.models';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import {
  ActionDialogData,
  ActionDialogMode,
  ActionDialogResult,
  TicketActionDialogComponent,
} from './ticket-action-dialog.component';
import { TicketAction, TicketDetail } from './tickets.models';
import { TicketsService } from './tickets.service';

/** What an action needs to know about the ticket (works from a list row as well as from the detail). */
export interface ActionTarget {
  id: number;
  reference: string;
  categoryId: number;
  subcategoryId: number | null;
  priority: PriorityCode;
}

/** Workflow actions shown as buttons (comments, attachments, feedback and edits have their own forms). */
export const WORKFLOW_ACTIONS: TicketAction[] = [
  'TAKE',
  'ASSIGN',
  'START',
  'PUT_ON_HOLD',
  'RESUME',
  'RESOLVE',
  'CONFIRM_RESOLUTION',
  'REJECT_RESOLUTION',
  'CLOSE',
  'REOPEN',
  'RECLASSIFY',
  'REJECT',
];

export const ACTION_LABELS: Partial<Record<TicketAction, string>> = {
  TAKE: 'Prendre en charge',
  ASSIGN: 'Affecter',
  START: 'Démarrer',
  PUT_ON_HOLD: 'Mettre en attente',
  RESUME: 'Reprendre',
  RESOLVE: 'Résoudre',
  CONFIRM_RESOLUTION: 'Confirmer la résolution',
  REJECT_RESOLUTION: 'Ce n’est pas résolu',
  CLOSE: 'Clôturer',
  REOPEN: 'Rouvrir',
  RECLASSIFY: 'Reclasser',
  REJECT: 'Rejeter',
};

const SUCCESS: Partial<Record<TicketAction, string>> = {
  TAKE: 'Ticket pris en charge.',
  ASSIGN: 'Ticket affecté.',
  START: 'Ticket démarré.',
  PUT_ON_HOLD: 'Ticket mis en attente.',
  RESUME: 'Ticket repris.',
  RESOLVE: 'Ticket résolu : le demandeur va être notifié.',
  CONFIRM_RESOLUTION: 'Merci ! Le ticket est fermé.',
  REJECT_RESOLUTION: 'Le ticket est renvoyé au technicien.',
  CLOSE: 'Ticket clôturé.',
  REOPEN: 'Ticket rouvert.',
  RECLASSIFY: 'Ticket reclassé.',
  REJECT: 'Ticket rejeté.',
};

/**
 * Runs a workflow action: asks what it needs (confirmation, reason, resolution, technician…), calls the API and
 * reports the outcome. Emits the updated ticket; completes without value when cancelled or refused.
 */
@Injectable({ providedIn: 'root' })
export class TicketActionsService {
  private readonly api = inject(TicketsService);
  private readonly dialog = inject(Dialog);
  private readonly confirmService = inject(ConfirmService);
  private readonly toasts = inject(ToastService);

  run(action: TicketAction, target: ActionTarget): Observable<TicketDetail> {
    return this.execute(action, target).pipe(
      tap(() => {
        const message = SUCCESS[action];
        if (message) {
          this.toasts.success(message);
        }
      }),
      catchError((error: unknown) => {
        this.toasts.error(toApiProblem(error).detail);
        return EMPTY;
      }),
    );
  }

  private execute(action: TicketAction, t: ActionTarget): Observable<TicketDetail> {
    switch (action) {
      case 'TAKE':
        return this.api.take(t.id);
      case 'START':
      case 'RESUME':
        return this.api.changeStatus(t.id, { targetStatus: 'IN_PROGRESS' });
      case 'PUT_ON_HOLD':
        return this.ask('hold', t).pipe(
          switchMap((r) =>
            r.mode === 'hold'
              ? this.api.changeStatus(t.id, {
                  targetStatus: 'PENDING',
                  pendingReason: r.pendingReason,
                  reason: r.comment || undefined,
                })
              : EMPTY,
          ),
        );
      case 'RESOLVE':
        return this.ask('resolve', t).pipe(
          switchMap((r) =>
            r.mode === 'resolve'
              ? this.api.changeStatus(t.id, {
                  targetStatus: 'RESOLVED',
                  resolution: r.resolution || undefined,
                })
              : EMPTY,
          ),
        );
      case 'ASSIGN':
        return this.ask('assign', t).pipe(
          switchMap((r) =>
            r.mode === 'assign' ? this.api.assign(t.id, r.technicianId, r.comment || null) : EMPTY,
          ),
        );
      case 'RECLASSIFY':
        return this.ask('reclassify', t).pipe(
          switchMap((r) =>
            r.mode === 'reclassify'
              ? this.api.reclassify(t.id, {
                  categoryId: r.categoryId,
                  subcategoryId: r.subcategoryId,
                  priority: r.priority,
                  reason: r.reason,
                })
              : EMPTY,
          ),
        );
      case 'REJECT':
        return this.confirm(t, {
          title: 'Rejeter le ticket',
          message: 'Le demandeur sera notifié avec votre motif. Le ticket sera clos.',
          confirmLabel: 'Rejeter',
          tone: 'danger',
          reason: true,
        }).pipe(
          switchMap((reason) => this.api.changeStatus(t.id, { targetStatus: 'REJECTED', reason })),
        );
      case 'CLOSE':
        return this.confirm(t, {
          title: 'Clôturer le ticket',
          message: 'Le ticket sera fermé sans attendre la confirmation du demandeur.',
          confirmLabel: 'Clôturer',
          tone: 'primary',
          reason: false,
        }).pipe(switchMap(() => this.api.changeStatus(t.id, { targetStatus: 'CLOSED' })));
      case 'CONFIRM_RESOLUTION':
        return this.confirm(t, {
          title: 'Confirmer la résolution',
          message: 'Votre problème est réglé ? Le ticket sera fermé.',
          confirmLabel: 'Oui, c’est résolu',
          tone: 'primary',
          reason: false,
        }).pipe(switchMap(() => this.api.confirmResolution(t.id)));
      case 'REJECT_RESOLUTION':
        return this.confirm(t, {
          title: 'Le problème n’est pas résolu',
          message: 'Le ticket repart chez le technicien, avec votre explication.',
          confirmLabel: 'Renvoyer au technicien',
          tone: 'danger',
          reason: true,
        }).pipe(switchMap((reason) => this.api.rejectResolution(t.id, reason)));
      case 'REOPEN':
        return this.confirm(t, {
          title: 'Rouvrir le ticket',
          message: 'Le ticket reprend son traitement là où il s’était arrêté.',
          confirmLabel: 'Rouvrir',
          tone: 'primary',
          reason: true,
        }).pipe(switchMap((reason) => this.api.reopen(t.id, reason)));
      default:
        return EMPTY;
    }
  }

  private ask(mode: ActionDialogMode, t: ActionTarget): Observable<ActionDialogResult> {
    const ref = this.dialog.open<ActionDialogResult, ActionDialogData>(
      TicketActionDialogComponent,
      {
        data: {
          mode,
          reference: t.reference,
          categoryId: t.categoryId,
          subcategoryId: t.subcategoryId,
          priority: t.priority,
        },
        ariaLabelledBy: 'nx-action-title',
        panelClass: 'nx-dialog-panel',
        backdropClass: 'nx-backdrop',
      },
    );
    return ref.closed.pipe(switchMap((result) => (result ? [result] : EMPTY)));
  }

  /** Emits the reason ("" without one) once confirmed; nothing when cancelled. */
  private confirm(
    t: ActionTarget,
    options: {
      title: string;
      message: string;
      confirmLabel: string;
      tone: 'primary' | 'danger';
      reason: boolean;
    },
  ): Observable<string> {
    return this.confirmService
      .confirm({
        title: `${options.title} · ${t.reference}`,
        message: options.message,
        confirmLabel: options.confirmLabel,
        tone: options.tone,
        reason: options.reason ? { label: 'Motif', required: true, maxLength: 1000 } : undefined,
      })
      .pipe(switchMap((result) => (result ? [result.reason ?? ''] : EMPTY)));
  }
}
