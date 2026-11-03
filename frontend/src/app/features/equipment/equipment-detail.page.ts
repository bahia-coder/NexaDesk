import { Dialog } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  ArrowLeft,
  CircleCheck,
  LucideAngularModule,
  LucideIconData,
  PackageOpen,
  Pencil,
  RefreshCw,
  Share2,
  TicketPlus,
  Trash2,
  TriangleAlert,
  UserPlus,
  Wrench,
} from 'lucide-angular';
import { Observable, catchError, of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { formatDateTime } from '../../core/format/time';
import { toApiProblem } from '../../core/http/api-error';
import { dataOf, loadState } from '../../core/http/load-state';
import { TicketSummary } from '../../core/models/ticket.models';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { AvatarComponent } from '../../shared/ui/avatar.component';
import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { SpinnerComponent } from '../../shared/ui/spinner.component';
import { TicketRowsComponent } from '../dashboard/ticket-rows.component';
import { AssignDialogComponent } from './assign-dialog.component';
import {
  EquipmentFormDialogComponent,
  EquipmentFormDialogData,
} from './equipment-form-dialog.component';
import {
  Assignment,
  EQUIPMENT_STATUS_LABELS,
  EQUIPMENT_STATUS_TONES,
  EQUIPMENT_TYPE_ICONS,
  EQUIPMENT_TYPE_LABELS,
  Equipment,
  EquipmentAction,
  equipmentActions,
} from './equipment.models';
import { EquipmentService } from './equipment.service';

const ACTION_BUTTONS: Record<EquipmentAction, { icon: LucideIconData; danger?: boolean }> = {
  ASSIGN: { icon: UserPlus },
  UNASSIGN: { icon: PackageOpen },
  SEND_TO_REPAIR: { icon: Wrench },
  BACK_IN_USE: { icon: CircleCheck },
  BACK_TO_STOCK: { icon: PackageOpen },
  DEPLOY_SHARED: { icon: Share2 },
  EDIT: { icon: Pencil },
  RETIRE: { icon: Trash2, danger: true },
};

/** One device: details, state changes allowed to the role, incident and assignment history. */
@Component({
  selector: 'nx-equipment-detail-page',
  imports: [
    RouterLink,
    LucideAngularModule,
    AvatarComponent,
    EmptyStateComponent,
    SkeletonComponent,
    SpinnerComponent,
    TicketRowsComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './equipment-detail.page.html',
  styleUrl: './equipment.scss',
})
export class EquipmentDetailPage {
  private readonly api = inject(EquipmentService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(Dialog);
  private readonly confirmService = inject(ConfirmService);
  private readonly toasts = inject(ToastService);

  /** Route parameter (component input binding). */
  readonly id = input.required<string>();

  protected readonly icons = { ArrowLeft, RefreshCw, TicketPlus, TriangleAlert };
  protected readonly buttons = ACTION_BUTTONS;
  protected readonly typeLabels = EQUIPMENT_TYPE_LABELS;
  protected readonly typeIcons = EQUIPMENT_TYPE_ICONS;
  protected readonly statusLabels = EQUIPMENT_STATUS_LABELS;
  protected readonly statusTones = EQUIPMENT_STATUS_TONES;
  protected readonly dataOf = dataOf;

  protected readonly employee = computed(() => this.auth.role() === 'EMPLOYEE');

  private readonly equipmentId = computed(() => Number(this.id()));
  private readonly reload = signal(0);
  protected readonly state = loadState(
    computed(() => ({ id: this.equipmentId(), attempt: this.reload() })),
    ({ id }) => this.api.get(id),
  );
  /** Replaced by each action's response. */
  protected readonly equipment = linkedSignal<Equipment | null>(() => dataOf(this.state()));

  /** Histories are for support staff (the API refuses them to employees). */
  private readonly related = signal(0);
  protected readonly assignments = loadState(
    computed(() => ({ id: this.equipmentId(), version: this.related(), staff: !this.employee() })),
    ({ id, staff }) =>
      staff ? this.api.assignments(id).pipe(catchError(() => of([] as Assignment[]))) : of([]),
  );
  protected readonly tickets = loadState(
    computed(() => ({ id: this.equipmentId(), staff: !this.employee() })),
    ({ id, staff }) =>
      staff ? this.api.tickets(id).pipe(catchError(() => of([] as TicketSummary[]))) : of([]),
  );

  protected readonly actions = computed(() => {
    const e = this.equipment();
    const role = this.auth.role();
    return e && role ? equipmentActions(e, role) : [];
  });
  /** In stock with no location: it cannot be deployed as a shared device yet. */
  protected readonly needsLocation = computed(() => {
    const e = this.equipment();
    return !!e && !this.employee() && e.status === 'IN_STOCK' && !e.assignedUser && !e.location;
  });
  protected readonly running = signal<EquipmentAction | null>(null);

  protected label(action: EquipmentAction, e: Equipment): string {
    switch (action) {
      case 'ASSIGN':
        return e.assignedUser ? 'Réaffecter' : 'Affecter à un utilisateur';
      case 'UNASSIGN':
      case 'BACK_TO_STOCK':
        return 'Remettre en stock';
      case 'SEND_TO_REPAIR':
        return 'Envoyer en réparation';
      case 'BACK_IN_USE':
        return 'Retour de réparation';
      case 'DEPLOY_SHARED':
        return 'Mettre en service (partagé)';
      case 'EDIT':
        return 'Modifier';
      case 'RETIRE':
        return 'Mettre au rebut';
    }
  }

  protected run(action: EquipmentAction, e: Equipment): void {
    switch (action) {
      case 'ASSIGN':
        this.dialog
          .open<Equipment, Equipment>(AssignDialogComponent, {
            data: e,
            ariaLabelledBy: 'nx-assign-title',
            panelClass: 'nx-dialog-panel',
            backdropClass: 'nx-backdrop',
          })
          .closed.subscribe((updated) => {
            if (updated) {
              this.applied(
                updated,
                `${updated.inventoryCode} affecté à ${updated.assignedUser?.fullName}.`,
              );
            }
          });
        return;
      case 'UNASSIGN':
        this.confirmService
          .confirm({
            title: `Remettre ${e.inventoryCode} en stock ?`,
            message: `${e.assignedUser?.fullName} n’en sera plus détenteur ; l’affectation est close dans l’historique.`,
            confirmLabel: 'Remettre en stock',
          })
          .subscribe((ok) => {
            if (ok) {
              this.execute(
                action,
                this.api.assign(e.id, null),
                `${e.inventoryCode} remis en stock.`,
              );
            }
          });
        return;
      case 'SEND_TO_REPAIR':
        this.execute(
          action,
          this.api.changeStatus(e.id, 'IN_REPAIR'),
          `${e.inventoryCode} envoyé en réparation.`,
        );
        return;
      case 'BACK_IN_USE':
      case 'DEPLOY_SHARED':
        this.execute(
          action,
          this.api.changeStatus(e.id, 'IN_USE'),
          `${e.inventoryCode} en service.`,
        );
        return;
      case 'BACK_TO_STOCK':
        this.execute(
          action,
          this.api.changeStatus(e.id, 'IN_STOCK'),
          `${e.inventoryCode} remis en stock.`,
        );
        return;
      case 'EDIT':
        this.dialog
          .open<Equipment, EquipmentFormDialogData>(EquipmentFormDialogComponent, {
            data: { equipment: e },
            ariaLabelledBy: 'nx-equipment-form-title',
            panelClass: 'nx-dialog-panel',
            backdropClass: 'nx-backdrop',
          })
          .closed.subscribe((updated) => {
            if (updated) {
              this.applied(updated, 'Équipement modifié.');
            }
          });
        return;
      case 'RETIRE':
        this.confirmService
          .confirm({
            title: `Mettre ${e.inventoryCode} au rebut ?`,
            message:
              'L’équipement sort définitivement du parc : il ne pourra plus être affecté ni modifié. Son historique est conservé.',
            confirmLabel: 'Mettre au rebut',
            tone: 'danger',
          })
          .subscribe((ok) => {
            if (ok) {
              this.execute(action, this.api.retire(e.id), `${e.inventoryCode} mis au rebut.`);
            }
          });
        return;
    }
  }

  private execute(action: EquipmentAction, request: Observable<Equipment>, success: string): void {
    this.running.set(action);
    request.subscribe({
      next: (updated) => {
        this.running.set(null);
        this.applied(updated, success);
      },
      error: (error: unknown) => {
        this.running.set(null);
        this.toasts.error(toApiProblem(error).detail);
      },
    });
  }

  private applied(updated: Equipment, message: string): void {
    this.equipment.set(updated);
    this.related.update((n) => n + 1);
    this.toasts.success(message);
  }

  protected retry(): void {
    this.reload.update((n) => n + 1);
  }

  protected day(value: string | null): string {
    return value ? value.split('-').reverse().join('/') : '—';
  }

  protected exact(at: string): string {
    return formatDateTime(at);
  }
}
