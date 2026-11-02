import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  CircleCheck,
  Clock,
  Inbox,
  LucideAngularModule,
  Moon,
  Sun,
  Ticket,
  TriangleAlert,
} from 'lucide-angular';

import { PriorityCode, SlaView, TicketStatus } from '../../core/models/ticket.models';
import { ConfirmService } from '../../core/services/confirm.service';
import { ThemeService } from '../../core/services/theme.service';
import { ToastService } from '../../core/services/toast.service';
import { AvatarComponent } from '../../shared/ui/avatar.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PriorityBadgeComponent } from '../../shared/ui/priority-badge.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { SlaIndicatorComponent } from '../../shared/ui/sla-indicator.component';
import { SpinnerComponent } from '../../shared/ui/spinner.component';
import { StatCardComponent } from '../../shared/ui/stat-card.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';

const HOUR = 3_600_000;

function sla(state: SlaView['state'], hoursLeft: number, consumed: number): SlaView {
  return {
    state,
    firstResponseDueAt: null,
    firstRespondedAt: null,
    firstResponseBreached: false,
    resolutionDueAt: new Date(Date.now() + hoursLeft * HOUR).toISOString(),
    consumedPercent: consumed,
    businessHoursOnly: true,
  };
}

/**
 * Development-only style guide (the route exists only in dev builds): every design-system component in each of
 * its states, to check them in light and dark themes. The values are fixtures for the components, not data.
 */
@Component({
  selector: 'nx-design-system-page',
  imports: [
    LucideAngularModule,
    AvatarComponent,
    EmptyStateComponent,
    PriorityBadgeComponent,
    SkeletonComponent,
    SlaIndicatorComponent,
    SpinnerComponent,
    StatCardComponent,
    StatusBadgeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './design-system.page.html',
  styleUrl: './design-system.page.scss',
})
export class DesignSystemPage {
  protected readonly theme = inject(ThemeService);
  private readonly toasts = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);

  protected readonly icons = { CircleCheck, Clock, Inbox, Moon, Sun, Ticket, TriangleAlert };
  protected readonly statuses: TicketStatus[] = [
    'NEW',
    'ASSIGNED',
    'IN_PROGRESS',
    'PENDING',
    'RESOLVED',
    'CLOSED',
    'REJECTED',
  ];
  protected readonly priorities: PriorityCode[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  protected readonly slas: { label: string; view: SlaView }[] = [
    { label: 'Dans les temps', view: sla('ON_TRACK', 26, 30) },
    { label: 'À risque', view: sla('AT_RISK', 1.4, 82) },
    { label: 'Dépassé', view: sla('BREACHED', -0.6, 100) },
    { label: 'En pause', view: sla('PAUSED', 5, 40) },
    { label: 'Respecté', view: sla('MET', 0, 60) },
    {
      label: 'Sans SLA',
      view: { ...sla('NONE', 0, 0), resolutionDueAt: null, consumedPercent: null },
    },
  ];

  protected readonly loading = signal(false);
  protected readonly lastConfirm = signal('—');

  toast(kind: 'success' | 'error' | 'info' | 'warning'): void {
    const messages = {
      success: 'Ticket INC-2026-0042 créé.',
      error: 'Une erreur inattendue est survenue.',
      info: 'Nouvelle notification.',
      warning: 'Le SLA du ticket arrive à échéance.',
    };
    this.toasts[kind](messages[kind], kind === 'error' ? 'Référence : 8f3a2c1d' : undefined);
  }

  simulateLoading(): void {
    this.loading.set(true);
    setTimeout(() => this.loading.set(false), 1500);
  }

  confirm(withReason: boolean): void {
    this.confirmService
      .confirm({
        title: withReason ? 'Rejeter le ticket' : 'Archiver l’article',
        message: withReason
          ? 'Le demandeur sera notifié avec votre motif. Le ticket sera clos.'
          : 'L’article disparaîtra de la recherche et des suggestions.',
        confirmLabel: withReason ? 'Rejeter' : 'Archiver',
        tone: 'danger',
        reason: withReason ? { label: 'Motif', required: true, maxLength: 1000 } : undefined,
      })
      .subscribe((result) =>
        this.lastConfirm.set(
          result === null ? 'Annulé' : `Confirmé${result.reason ? ' : ' + result.reason : ''}`,
        ),
      );
  }
}
