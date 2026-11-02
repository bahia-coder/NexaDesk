import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  ArrowLeft,
  BookOpen,
  CircleAlert,
  FileText,
  LucideAngularModule,
  Paperclip,
  Sparkles,
  Wrench,
  X,
  Zap,
} from 'lucide-angular';
import {
  catchError,
  concatMap,
  debounceTime,
  distinctUntilChanged,
  filter,
  from,
  map,
  of,
  switchMap,
  toArray,
} from 'rxjs';

import { Classification, SimilarContent } from '../../../core/ai/ai.models';
import { AiService } from '../../../core/ai/ai.service';
import { toApiProblem } from '../../../core/http/api-error';
import { TicketType } from '../../../core/models/ticket.models';
import { ToastService } from '../../../core/services/toast.service';
import { AiSourceBadgeComponent } from '../../../shared/ui/ai-source-badge.component';
import { PriorityBadgeComponent } from '../../../shared/ui/priority-badge.component';
import { SpinnerComponent } from '../../../shared/ui/spinner.component';
import { ACCEPTED_FILES, MAX_FILE_BYTES } from '../detail/ticket-attachments.component';
import {
  IMPACT_LABELS,
  TYPE_LABELS,
  URGENCY_LABELS,
  formatSize,
  priorityFor,
} from '../tickets.labels';
import { Category, EquipmentRef, Level } from '../tickets.models';
import { TicketsService } from '../tickets.service';

const MAX_FILES = 5;

/**
 * New ticket in three steps: type, details (with the AI category suggestion and "maybe already solved"), review.
 * The priority shown comes from the same impact × urgency matrix as the server, which stores its own result.
 */
@Component({
  selector: 'nx-ticket-create-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    LucideAngularModule,
    AiSourceBadgeComponent,
    PriorityBadgeComponent,
    SpinnerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './ticket-create.page.html',
  styleUrl: './ticket-create.page.scss',
})
export class TicketCreatePage {
  private readonly api = inject(TicketsService);
  private readonly ai = inject(AiService);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);

  protected readonly icons = {
    ArrowLeft,
    BookOpen,
    CircleAlert,
    FileText,
    Paperclip,
    Sparkles,
    Wrench,
    X,
    Zap,
  };
  protected readonly labels = { type: TYPE_LABELS, impact: IMPACT_LABELS, urgency: URGENCY_LABELS };
  protected readonly levels: Level[] = ['LOW', 'MEDIUM', 'HIGH'];
  protected readonly accepted = ACCEPTED_FILES;

  protected readonly step = signal<1 | 2 | 3>(1);

  protected readonly form = inject(NonNullableFormBuilder).group({
    type: ['INCIDENT' as TicketType, Validators.required],
    title: ['', [Validators.required, Validators.minLength(5), Validators.maxLength(150)]],
    description: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(5000)]],
    categoryId: [0, Validators.min(1)],
    subcategoryId: [0],
    impact: ['LOW' as Level, Validators.required],
    urgency: ['MEDIUM' as Level, Validators.required],
    equipmentId: [0],
  });

  private readonly values = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  protected readonly categories = toSignal(
    this.api.categories().pipe(catchError(() => of([] as Category[]))),
    { initialValue: [] as Category[] },
  );
  protected readonly equipment = toSignal(
    this.api.selectableEquipment().pipe(catchError(() => of([] as EquipmentRef[]))),
    { initialValue: [] as EquipmentRef[] },
  );
  protected readonly subcategories = computed(
    () =>
      this.categories()
        .find((c) => c.id === Number(this.values().categoryId))
        ?.subcategories.filter((s) => s.active) ?? [],
  );
  protected readonly priority = computed(() =>
    priorityFor(this.values().impact ?? 'LOW', this.values().urgency ?? 'MEDIUM'),
  );

  // ---- AI help, refreshed while the user types ---------------------------------------------------------
  protected readonly suggestion = signal<Classification | null>(null);
  protected readonly suggestionDismissed = signal(false);
  protected readonly similar = signal<SimilarContent | null>(null);
  protected readonly analysing = signal(false);

  protected readonly files = signal<File[]>([]);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    const text$ = this.form.valueChanges.pipe(
      map(() => ({
        title: this.form.controls.title.value.trim(),
        description: this.form.controls.description.value.trim(),
      })),
      filter((text) => text.title.length >= 5),
      debounceTime(700),
      distinctUntilChanged((a, b) => a.title === b.title && a.description === b.description),
      takeUntilDestroyed(),
    );
    text$
      .pipe(
        switchMap(({ title, description }) => {
          this.analysing.set(true);
          return this.ai.classify(title, description).pipe(catchError(() => of(null)));
        }),
      )
      .subscribe((result) => {
        this.analysing.set(false);
        this.suggestion.set(result?.category ? result : null);
      });
    text$
      .pipe(
        switchMap(({ title, description }) =>
          this.ai.similar(title, description).pipe(catchError(() => of(null))),
        ),
      )
      .subscribe((result) => this.similar.set(result));

    this.form.controls.categoryId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.form.controls.subcategoryId.setValue(0, { emitEvent: false }));
  }

  protected chooseType(type: TicketType): void {
    this.form.controls.type.setValue(type);
    this.step.set(2);
  }

  protected applySuggestion(s: Classification): void {
    this.form.patchValue({
      categoryId: s.category?.id ?? 0,
      impact: s.impact ?? this.form.controls.impact.value,
      urgency: s.urgency ?? this.form.controls.urgency.value,
    });
    this.form.controls.subcategoryId.setValue(s.subcategory?.id ?? 0);
    this.suggestionDismissed.set(true);
  }

  protected addFiles(input: HTMLInputElement): void {
    const picked = Array.from(input.files ?? []);
    input.value = '';
    const tooBig = picked.filter((file) => file.size > MAX_FILE_BYTES);
    if (tooBig.length) {
      this.toasts.error(
        `Fichier trop volumineux (10 Mo maximum) : ${tooBig.map((f) => f.name).join(', ')}`,
      );
    }
    const next = [...this.files(), ...picked.filter((file) => file.size <= MAX_FILE_BYTES)];
    if (next.length > MAX_FILES) {
      this.toasts.warning(`${MAX_FILES} fichiers au maximum par ticket.`);
    }
    this.files.set(next.slice(0, MAX_FILES));
  }

  protected removeFile(file: File): void {
    this.files.update((list) => list.filter((f) => f !== file));
  }

  protected toReview(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.step.set(3);
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      return;
    }
    const v = this.form.getRawValue();
    this.submitting.set(true);
    this.error.set(null);
    this.api
      .create({
        type: v.type,
        title: v.title.trim(),
        description: v.description.trim(),
        categoryId: v.categoryId,
        subcategoryId: v.subcategoryId || null,
        impact: v.impact,
        urgency: v.urgency,
        equipmentId: v.equipmentId || null,
      })
      .pipe(
        // Files are sent once the ticket exists; one failure does not undo the ticket.
        switchMap((ticket) =>
          from(this.files()).pipe(
            concatMap((file) =>
              this.api.upload(ticket.id, file).pipe(
                map(() => null),
                catchError(() => of(file.name)),
              ),
            ),
            toArray(),
            map((failed) => ({
              ticket,
              failed: failed.filter((name): name is string => name !== null),
            })),
          ),
        ),
      )
      .subscribe({
        next: ({ ticket, failed }) => {
          this.toasts.success(`Ticket ${ticket.reference} créé.`);
          if (failed.length) {
            this.toasts.warning(`Pièces jointes refusées : ${failed.join(', ')}`);
          }
          void this.router.navigate(['/tickets', ticket.id]);
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.error.set(toApiProblem(error).detail);
        },
      });
  }

  protected invalid(name: 'title' | 'description' | 'categoryId'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }

  protected categoryName(id: number): string {
    return this.categories().find((c) => c.id === id)?.name ?? '';
  }

  protected subcategoryName(id: number): string {
    return this.subcategories().find((s) => s.id === id)?.name ?? '';
  }

  protected equipmentLabel(id: number): string {
    const e = this.equipment().find((item) => item.id === id);
    return e ? `${e.inventoryCode} · ${e.name}` : '';
  }

  protected size(bytes: number): string {
    return formatSize(bytes);
  }
}
