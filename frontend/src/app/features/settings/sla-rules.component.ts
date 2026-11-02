import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  linkedSignal,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';

import { formatDuration } from '../../core/format/time';
import { toApiProblem } from '../../core/http/api-error';
import { dataOf, loadState } from '../../core/http/load-state';
import { ToastService } from '../../core/services/toast.service';
import { PriorityBadgeComponent } from '../../shared/ui/priority-badge.component';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { SpinnerComponent } from '../../shared/ui/spinner.component';
import { SlaRule } from './settings.models';
import { SettingsService } from './settings.service';

/**
 * Readable target. Business-hours targets stay in hours ("24 h ouvrées"): a business day is not 24 hours, so
 * "1 j" would be misleading.
 */
export function humanDuration(minutes: number, businessHoursOnly: boolean): string {
  if (!Number.isFinite(minutes) || minutes <= 0) {
    return '';
  }
  if (!businessHoursOnly) {
    return formatDuration(minutes * 60_000);
  }
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  const text =
    hours === 0
      ? `${rest} min`
      : rest === 0
        ? `${hours} h`
        : `${hours} h ${String(rest).padStart(2, '0')}`;
  return `${text} ouvrées`;
}

/** Same limits as the API (SlaRuleRequest and the table check). */
export function ruleError(rule: SlaRule): string | null {
  const whole = (n: number) => Number.isInteger(n) && n >= 1;
  if (!whole(rule.firstResponseMinutes) || rule.firstResponseMinutes > 10_080) {
    return 'Première réponse : de 1 minute à 7 jours.';
  }
  if (!whole(rule.resolutionMinutes) || rule.resolutionMinutes > 43_200) {
    return 'Résolution : de 1 minute à 30 jours.';
  }
  if (rule.resolutionMinutes <= rule.firstResponseMinutes) {
    return 'La résolution doit laisser plus de temps que la première réponse.';
  }
  if (
    !Number.isInteger(rule.warningThresholdPercent) ||
    rule.warningThresholdPercent < 50 ||
    rule.warningThresholdPercent > 95
  ) {
    return 'Le seuil d’alerte va de 50 à 95 %.';
  }
  return null;
}

/** One rule per priority: response and resolution targets, 24/7 or business hours, warning threshold. */
@Component({
  selector: 'nx-sla-rules',
  imports: [FormsModule, PriorityBadgeComponent, SkeletonComponent, SpinnerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card panel">
      <header>
        <h2 class="card__title">Règles par priorité</h2>
        <p class="muted small">
          Les modifications s’appliquent aux tickets créés ensuite ; les échéances déjà calculées ne
          changent pas.
        </p>
      </header>
      @if (rows(); as list) {
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th scope="col">Priorité</th>
                <th scope="col">Première réponse (min)</th>
                <th scope="col">Résolution (min)</th>
                <th scope="col">Décompte</th>
                <th scope="col">Alerte à</th>
                <th scope="col"><span class="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              @for (rule of list; track rule.id; let i = $index) {
                <tr>
                  <th scope="row">
                    <nx-priority-badge [code]="rule.priority" [label]="rule.priorityLabel" />
                  </th>
                  <td>
                    <input
                      class="input num"
                      type="number"
                      min="1"
                      max="10080"
                      [attr.aria-label]="'Première réponse, ' + rule.priorityLabel"
                      [ngModel]="rule.firstResponseMinutes"
                      (ngModelChange)="edit(i, { firstResponseMinutes: +$event })"
                    />
                    <span class="muted small">{{
                      human(rule.firstResponseMinutes, rule.businessHoursOnly)
                    }}</span>
                  </td>
                  <td>
                    <input
                      class="input num"
                      type="number"
                      min="1"
                      max="43200"
                      [attr.aria-label]="'Résolution, ' + rule.priorityLabel"
                      [ngModel]="rule.resolutionMinutes"
                      (ngModelChange)="edit(i, { resolutionMinutes: +$event })"
                    />
                    <span class="muted small">{{
                      human(rule.resolutionMinutes, rule.businessHoursOnly)
                    }}</span>
                  </td>
                  <td>
                    <select
                      class="select"
                      [attr.aria-label]="'Décompte, ' + rule.priorityLabel"
                      [ngModel]="rule.businessHoursOnly"
                      (ngModelChange)="edit(i, { businessHoursOnly: $event })"
                    >
                      <option [ngValue]="true">Heures ouvrées</option>
                      <option [ngValue]="false">24 h / 24, 7 j / 7</option>
                    </select>
                  </td>
                  <td>
                    <input
                      class="input num"
                      type="number"
                      min="50"
                      max="95"
                      [attr.aria-label]="'Seuil d’alerte en pourcentage, ' + rule.priorityLabel"
                      [ngModel]="rule.warningThresholdPercent"
                      (ngModelChange)="edit(i, { warningThresholdPercent: +$event })"
                    />
                    <span class="muted small">% du délai</span>
                  </td>
                  <td class="row-actions">
                    @if (dirty(i)) {
                      @if (errorOf(rule); as message) {
                        <span class="field__error">{{ message }}</span>
                      }
                      <button
                        type="button"
                        class="btn btn--primary btn--sm"
                        [disabled]="!!errorOf(rule) || saving() === rule.id"
                        (click)="save(i)"
                      >
                        @if (saving() === rule.id) {
                          <nx-spinner [size]="14" />
                        }
                        Enregistrer
                      </button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <nx-skeleton height="44px" [lines]="4" />
      }
    </section>
  `,
  styleUrl: './settings.scss',
})
export class SlaRulesComponent {
  private readonly api = inject(SettingsService);
  private readonly toasts = inject(ToastService);

  private readonly state = loadState(signal(0), () => this.api.rules());
  /** Saved values, to know which rows changed. */
  private readonly saved = linkedSignal(() => dataOf(this.state()) ?? []);
  /** Values being edited. */
  protected readonly rows = linkedSignal<SlaRule[] | null>(() => {
    const data = dataOf(this.state());
    return data ? data.map((rule) => ({ ...rule })) : null;
  });
  protected readonly saving = signal<number | null>(null);
  private readonly savedById = computed(() => new Map(this.saved().map((r) => [r.id, r])));

  protected edit(index: number, change: Partial<SlaRule>): void {
    this.rows.update(
      (rows) => rows?.map((rule, i) => (i === index ? { ...rule, ...change } : rule)) ?? rows,
    );
  }

  protected dirty(index: number): boolean {
    const rule = this.rows()?.[index];
    const saved = rule && this.savedById().get(rule.id);
    return !!rule && !!saved && JSON.stringify(rule) !== JSON.stringify(saved);
  }

  protected errorOf(rule: SlaRule): string | null {
    return ruleError(rule);
  }

  protected save(index: number): void {
    const rule = this.rows()?.[index];
    if (!rule || ruleError(rule)) {
      return;
    }
    this.saving.set(rule.id);
    this.api
      .updateRule(rule.id, {
        firstResponseMinutes: rule.firstResponseMinutes,
        resolutionMinutes: rule.resolutionMinutes,
        businessHoursOnly: rule.businessHoursOnly,
        warningThresholdPercent: rule.warningThresholdPercent,
      })
      .subscribe({
        next: (updated) => {
          this.saving.set(null);
          this.saved.update((list) => list.map((r) => (r.id === updated.id ? updated : r)));
          this.rows.update(
            (rows) => rows?.map((r) => (r.id === updated.id ? { ...updated } : r)) ?? rows,
          );
          this.toasts.success(`Règle « ${updated.priorityLabel} » enregistrée.`);
        },
        error: (error: unknown) => {
          this.saving.set(null);
          this.toasts.error(toApiProblem(error).detail);
        },
      });
  }

  protected human(minutes: number, businessHoursOnly: boolean): string {
    return humanDuration(minutes, businessHoursOnly);
  }
}
