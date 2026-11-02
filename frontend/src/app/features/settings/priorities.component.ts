import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  linkedSignal,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';

import { toApiProblem } from '../../core/http/api-error';
import { dataOf, loadState } from '../../core/http/load-state';
import { ToastService } from '../../core/services/toast.service';
import { PriorityBadgeComponent } from '../../shared/ui/priority-badge.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { IMPACT_LABELS, URGENCY_LABELS } from '../tickets/tickets.labels';
import { Level } from '../tickets/tickets.models';
import { PriorityItem } from './settings.models';
import { SettingsService } from './settings.service';

const COLOR = /^#[0-9A-Fa-f]{6}$/;

/**
 * The four priorities are fixed (their rules live in the code and the SLA); the admin renames them and picks their
 * colour. The impact × urgency matrix that derives them is shown for reference.
 */
@Component({
  selector: 'nx-priorities',
  imports: [FormsModule, PriorityBadgeComponent, SkeletonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="grid-2">
      <section class="card panel">
        <h2 class="card__title">Priorités</h2>
        <p class="muted small">
          Le libellé apparaît partout ; la couleur sert aux graphiques (les badges gardent leurs
          teintes accessibles).
        </p>
        @if (rows(); as list) {
          <ul class="priorities">
            @for (p of list; track p.id; let i = $index) {
              <li>
                <nx-priority-badge [code]="p.code" [label]="p.label" />
                <input
                  class="input"
                  maxlength="50"
                  [attr.aria-label]="'Libellé de la priorité ' + p.code"
                  [ngModel]="p.label"
                  (ngModelChange)="edit(i, { label: $event })"
                />
                <input
                  class="color"
                  type="color"
                  [attr.aria-label]="'Couleur de la priorité ' + p.code"
                  [ngModel]="p.color"
                  (ngModelChange)="edit(i, { color: $event })"
                />
                <button
                  type="button"
                  class="btn btn--primary btn--sm"
                  [disabled]="!dirty(p) || !p.label.trim() || !validColor(p.color)"
                  (click)="save(p)"
                >
                  Enregistrer
                </button>
              </li>
            }
          </ul>
        } @else {
          <nx-skeleton height="36px" [lines]="4" />
        }
      </section>

      <section class="card panel">
        <h2 class="card__title">Matrice impact × urgence</h2>
        <p class="muted small">
          La priorité d’un ticket découle de ces deux réponses de l’employé.
        </p>
        @if (matrix(); as m) {
          <table class="table matrix">
            <thead>
              <tr>
                <th scope="col">Impact \\ Urgence</th>
                @for (u of levels; track u) {
                  <th scope="col">{{ urgencyLabels[u] }}</th>
                }
              </tr>
            </thead>
            <tbody>
              @for (i of levelsDesc; track i) {
                <tr>
                  <th scope="row">{{ impactLabels[i] }}</th>
                  @for (u of levels; track u) {
                    <td><nx-priority-badge [code]="m[i + '-' + u]" /></td>
                  }
                </tr>
              }
            </tbody>
          </table>
        } @else {
          <nx-skeleton height="32px" [lines]="4" />
        }
      </section>
    </div>
  `,
  styleUrl: './settings.scss',
})
export class PrioritiesComponent {
  private readonly api = inject(SettingsService);
  private readonly toasts = inject(ToastService);

  protected readonly levels: Level[] = ['LOW', 'MEDIUM', 'HIGH'];
  protected readonly levelsDesc: Level[] = ['HIGH', 'MEDIUM', 'LOW'];
  protected readonly impactLabels = IMPACT_LABELS;
  protected readonly urgencyLabels = URGENCY_LABELS;

  private readonly state = loadState(signal(0), () => this.api.priorities());
  private readonly saved = linkedSignal(() => dataOf(this.state()) ?? []);
  protected readonly rows = linkedSignal<PriorityItem[] | null>(
    () => dataOf(this.state())?.map((p) => ({ ...p })) ?? null,
  );
  private readonly matrixState = loadState(signal(0), () => this.api.matrix());
  /** "HIGH-LOW" → priority code. */
  protected readonly matrix = computed(() => {
    const entries = dataOf(this.matrixState());
    return entries
      ? Object.fromEntries(entries.map((e) => [`${e.impact}-${e.urgency}`, e.priority]))
      : null;
  });

  protected edit(index: number, change: Partial<PriorityItem>): void {
    this.rows.update(
      (rows) => rows?.map((p, i) => (i === index ? { ...p, ...change } : p)) ?? rows,
    );
  }

  protected dirty(p: PriorityItem): boolean {
    const saved = this.saved().find((s) => s.id === p.id);
    return (
      !!saved && (saved.label !== p.label || saved.color.toLowerCase() !== p.color.toLowerCase())
    );
  }

  protected validColor(color: string): boolean {
    return COLOR.test(color);
  }

  protected save(p: PriorityItem): void {
    this.api.updatePriority(p.id, p.label.trim(), p.color).subscribe({
      next: (updated) => {
        this.saved.update((list) => list.map((s) => (s.id === updated.id ? updated : s)));
        this.toasts.success(`Priorité « ${updated.label} » enregistrée.`);
      },
      error: (error: unknown) => this.toasts.error(toApiProblem(error).detail),
    });
  }
}
