import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  linkedSignal,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LucideAngularModule, Trash2 } from 'lucide-angular';
import { switchMap } from 'rxjs';

import { PRIORITY_LABELS } from '../../core/format/labels';
import { COMPANY_TIME_ZONE, formatDateTime } from '../../core/format/time';
import { toApiProblem } from '../../core/http/api-error';
import { dataOf, loadState } from '../../core/http/load-state';
import { PriorityCode } from '../../core/models/ticket.models';
import { ClockService } from '../../core/services/clock.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { SpinnerComponent } from '../../shared/ui/spinner.component';
import { DAYS, DayOfWeek, Holiday, SlaPreview } from './settings.models';
import { SettingsService } from './settings.service';
import { nowInZone, zonedToInstant } from './zoned-time';

const DAY_FORMAT = new Intl.DateTimeFormat('fr-FR', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
});

/** Business hours, public holidays, and a deadline simulator to check the whole configuration. */
@Component({
  selector: 'nx-sla-calendar',
  imports: [FormsModule, LucideAngularModule, SkeletonComponent, SpinnerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="grid-2">
      <section class="card panel">
        <h2 class="card__title">Heures ouvrées</h2>
        @if (calendar(); as c) {
          <p class="muted small">
            Fuseau de l’entreprise : {{ c.timeZone }} (paramètre de déploiement).
          </p>
          <div class="grid-2">
            <label class="field">
              <span class="field__label">Début</span>
              <input
                class="input"
                type="time"
                [ngModel]="workStart()"
                (ngModelChange)="workStart.set($event)"
              />
            </label>
            <label class="field">
              <span class="field__label">Fin</span>
              <input
                class="input"
                type="time"
                [ngModel]="workEnd()"
                (ngModelChange)="workEnd.set($event)"
              />
            </label>
          </div>
          <fieldset class="days">
            <legend class="field__label">Jours ouvrés</legend>
            @for (d of days; track d.day) {
              <label class="checkbox">
                <input
                  type="checkbox"
                  [checked]="workingDays().includes(d.day)"
                  (change)="toggleDay(d.day, $any($event.target).checked)"
                />
                {{ d.label }}
              </label>
            }
          </fieldset>
          @if (calendarError(); as message) {
            <p class="field__error" role="alert">{{ message }}</p>
          }
          <div class="form-actions">
            <button
              type="button"
              class="btn btn--primary"
              [disabled]="savingCalendar()"
              (click)="saveCalendar()"
            >
              @if (savingCalendar()) {
                <nx-spinner />
              }
              Enregistrer les horaires
            </button>
          </div>
        } @else {
          <nx-skeleton height="36px" [lines]="4" />
        }
      </section>

      <section class="card panel">
        <h2 class="card__title">Simuler une échéance</h2>
        <p class="muted small">
          Échéances d’un ticket créé au moment choisi, avec la configuration actuelle.
        </p>
        <div class="grid-2">
          <label class="field">
            <span class="field__label">Priorité</span>
            <select class="select" [(ngModel)]="previewPriority">
              @for (p of priorities; track p) {
                <option [value]="p">{{ priorityLabels[p] }}</option>
              }
            </select>
          </label>
          <label class="field">
            <span class="field__label">Créé le (heure de l’entreprise)</span>
            <input class="input" type="datetime-local" [(ngModel)]="previewFrom" />
          </label>
        </div>
        <div class="form-actions">
          <button
            type="button"
            class="btn btn--secondary"
            [disabled]="previewing()"
            (click)="runPreview()"
          >
            @if (previewing()) {
              <nx-spinner />
            }
            Calculer
          </button>
        </div>
        @if (preview(); as p) {
          <dl class="facts">
            <dt>Décompte</dt>
            <dd>{{ p.businessHoursOnly ? 'Heures ouvrées' : '24 h / 24, 7 j / 7' }}</dd>
            <dt>Première réponse avant</dt>
            <dd>{{ exact(p.firstResponseDueAt) }}</dd>
            <dt>Résolution avant</dt>
            <dd>{{ exact(p.resolutionDueAt) }}</dd>
          </dl>
        }
      </section>
    </div>

    <section class="card panel">
      <h2 class="card__title">Jours fériés</h2>
      <p class="muted small">Ils ne comptent pas dans les délais en heures ouvrées.</p>
      <form class="holiday-form" (ngSubmit)="addHoliday()">
        <label class="field">
          <span class="field__label">Date</span>
          <input class="input" type="date" name="day" [(ngModel)]="newDay" required />
        </label>
        <label class="field grow">
          <span class="field__label">Libellé</span>
          <input
            class="input"
            name="label"
            [(ngModel)]="newLabel"
            maxlength="100"
            required
            placeholder="Ex. : Pont de l’Ascension"
          />
        </label>
        <button
          type="submit"
          class="btn btn--primary"
          [disabled]="!newDay || !newLabel.trim() || addingHoliday()"
        >
          Ajouter
        </button>
      </form>
      @if (holidays(); as list) {
        <ul class="holidays">
          @for (h of list; track h.id) {
            <li [class.past]="h.day < today()">
              <span
                ><strong>{{ dayLabel(h.day) }}</strong> · {{ h.label }}</span
              >
              <button
                type="button"
                class="btn btn--ghost btn--icon btn--sm"
                [attr.aria-label]="'Supprimer ' + h.label"
                (click)="removeHoliday(h)"
              >
                <lucide-icon [img]="icons.Trash2" [size]="16" aria-hidden="true" />
              </button>
            </li>
          } @empty {
            <li class="muted">Aucun jour férié déclaré.</li>
          }
        </ul>
      } @else {
        <nx-skeleton height="28px" [lines]="5" />
      }
    </section>
  `,
  styleUrl: './settings.scss',
})
export class SlaCalendarComponent {
  private readonly api = inject(SettingsService);
  private readonly toasts = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);
  private readonly clock = inject(ClockService);

  protected readonly icons = { Trash2 };
  protected readonly days = DAYS;
  protected readonly priorities = Object.keys(PRIORITY_LABELS) as PriorityCode[];
  protected readonly priorityLabels = PRIORITY_LABELS;

  // ---- Business hours ----------------------------------------------------------------------------------
  private readonly calendarState = loadState(signal(0), () => this.api.calendar());
  protected readonly calendar = computed(() => dataOf(this.calendarState()));
  /** "08:00:00" from the API → "08:00" for the time inputs. */
  protected readonly workStart = linkedSignal(() => this.calendar()?.workStart.slice(0, 5) ?? '');
  protected readonly workEnd = linkedSignal(() => this.calendar()?.workEnd.slice(0, 5) ?? '');
  protected readonly workingDays = linkedSignal<DayOfWeek[]>(
    () => this.calendar()?.workingDays ?? [],
  );
  protected readonly savingCalendar = signal(false);
  protected readonly calendarError = signal<string | null>(null);

  // ---- Holidays ----------------------------------------------------------------------------------------
  private readonly holidaysVersion = signal(0);
  private readonly holidaysState = loadState(this.holidaysVersion, () => this.api.holidays());
  protected readonly holidays = computed(() =>
    dataOf(this.holidaysState())
      ?.slice()
      .sort((a, b) => a.day.localeCompare(b.day)),
  );
  protected readonly today = computed(() =>
    nowInZone(this.clock.now(), COMPANY_TIME_ZONE).slice(0, 10),
  );
  protected newDay = '';
  protected newLabel = '';
  protected readonly addingHoliday = signal(false);

  // ---- Preview -----------------------------------------------------------------------------------------
  protected previewPriority: PriorityCode = 'HIGH';
  protected previewFrom = nowInZone(Date.now(), COMPANY_TIME_ZONE);
  protected readonly preview = signal<SlaPreview | null>(null);
  protected readonly previewing = signal(false);

  protected toggleDay(day: DayOfWeek, checked: boolean): void {
    this.workingDays.update((days) => (checked ? [...days, day] : days.filter((d) => d !== day)));
  }

  protected saveCalendar(): void {
    const start = this.workStart();
    const end = this.workEnd();
    if (!start || !end || end <= start) {
      this.calendarError.set('L’heure de fin doit être après l’heure de début.');
      return;
    }
    if (this.workingDays().length === 0) {
      this.calendarError.set('Au moins un jour ouvré est requis.');
      return;
    }
    this.calendarError.set(null);
    this.savingCalendar.set(true);
    const order = DAYS.map((d) => d.day);
    const days = [...this.workingDays()].sort((a, b) => order.indexOf(a) - order.indexOf(b));
    this.api.updateCalendar(`${start}:00`, `${end}:00`, days).subscribe({
      next: () => {
        this.savingCalendar.set(false);
        this.toasts.success(
          'Horaires enregistrés : ils s’appliquent aux prochains calculs d’échéance.',
        );
      },
      error: (error: unknown) => {
        this.savingCalendar.set(false);
        this.calendarError.set(toApiProblem(error).detail);
      },
    });
  }

  protected addHoliday(): void {
    if (!this.newDay || !this.newLabel.trim()) {
      return;
    }
    this.addingHoliday.set(true);
    this.api.addHoliday(this.newDay, this.newLabel.trim()).subscribe({
      next: () => {
        this.addingHoliday.set(false);
        this.newDay = '';
        this.newLabel = '';
        this.holidaysVersion.update((n) => n + 1);
        this.toasts.success('Jour férié ajouté.');
      },
      error: (error: unknown) => {
        this.addingHoliday.set(false);
        this.toasts.error(toApiProblem(error).detail);
      },
    });
  }

  protected removeHoliday(h: Holiday): void {
    this.confirmService
      .confirm({
        title: 'Supprimer le jour férié',
        message: `${this.dayLabel(h.day)} redeviendra un jour ouvré pour les prochains calculs.`,
        confirmLabel: 'Supprimer',
        tone: 'danger',
      })
      .pipe(switchMap((ok) => (ok ? this.api.deleteHoliday(h.id) : [])))
      .subscribe({
        next: () => {
          this.holidaysVersion.update((n) => n + 1);
          this.toasts.success('Jour férié supprimé.');
        },
        error: (error: unknown) => this.toasts.error(toApiProblem(error).detail),
      });
  }

  protected runPreview(): void {
    const from = zonedToInstant(this.previewFrom, COMPANY_TIME_ZONE);
    this.previewing.set(true);
    this.api.preview(this.previewPriority, from).subscribe({
      next: (result) => {
        this.previewing.set(false);
        this.preview.set(result);
      },
      error: (error: unknown) => {
        this.previewing.set(false);
        this.toasts.error(toApiProblem(error).detail);
      },
    });
  }

  protected dayLabel(day: string): string {
    return DAY_FORMAT.format(new Date(`${day}T00:00:00Z`));
  }

  protected exact(at: string): string {
    return formatDateTime(at);
  }
}
