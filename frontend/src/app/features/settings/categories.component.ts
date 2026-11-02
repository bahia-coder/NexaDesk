import { Dialog } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, linkedSignal, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Check, LucideAngularModule, Pencil, Plus, Power, X } from 'lucide-angular';
import { Observable } from 'rxjs';

import { toApiProblem } from '../../core/http/api-error';
import { LoadState, dataOf, loadState } from '../../core/http/load-state';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { SkeletonComponent } from '../../shared/ui/skeleton.component';
import { Category } from '../tickets/tickets.models';
import { CategoryDialogComponent } from './category-dialog.component';
import { SettingsService } from './settings.service';

/**
 * Ticket categories and their subcategories. Nothing is deleted (tickets keep referencing them): an obsolete one
 * is deactivated and no longer offered for new tickets.
 */
@Component({
  selector: 'nx-categories',
  imports: [FormsModule, LucideAngularModule, SkeletonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card panel">
      <header class="panel__head">
        <div>
          <h2 class="card__title">Catégories</h2>
          <p class="muted small">
            Désactiver une catégorie la retire des nouveaux tickets, sans toucher aux tickets
            existants.
          </p>
        </div>
        <button type="button" class="btn btn--primary" (click)="edit(null)">
          <lucide-icon [img]="icons.Plus" [size]="16" aria-hidden="true" /> Nouvelle catégorie
        </button>
      </header>

      @if (categories(); as list) {
        <ul class="categories">
          @for (c of list; track c.id) {
            <li class="category" [class.inactive]="!c.active">
              <div class="category__head">
                <div>
                  <strong>{{ c.name }}</strong>
                  @if (!c.active) {
                    <span class="badge badge--slate">Désactivée</span>
                  }
                  <span class="muted small block">{{ c.description }}</span>
                  @if (c.keywords) {
                    <span class="muted small block">Mots-clés : {{ c.keywords }}</span>
                  }
                </div>
                <div class="category__actions">
                  <button
                    type="button"
                    class="btn btn--ghost btn--icon btn--sm"
                    [attr.aria-label]="'Modifier ' + c.name"
                    (click)="edit(c)"
                  >
                    <lucide-icon [img]="icons.Pencil" [size]="16" aria-hidden="true" />
                  </button>
                  <button type="button" class="btn btn--ghost btn--sm" (click)="toggleCategory(c)">
                    <lucide-icon [img]="icons.Power" [size]="14" aria-hidden="true" />
                    {{ c.active ? 'Désactiver' : 'Réactiver' }}
                  </button>
                </div>
              </div>

              <ul class="subs" [attr.aria-label]="'Sous-catégories de ' + c.name">
                @for (s of c.subcategories; track s.id) {
                  <li class="sub" [class.inactive]="!s.active">
                    @if (renaming() === s.id) {
                      <input
                        class="input sub__input"
                        [attr.aria-label]="'Nouveau nom de ' + s.name"
                        [(ngModel)]="renameValue"
                        maxlength="80"
                        (keydown.enter)="rename(s.id)"
                        (keydown.escape)="renaming.set(null)"
                      />
                      <button
                        type="button"
                        class="btn btn--ghost btn--icon btn--sm"
                        aria-label="Valider"
                        (click)="rename(s.id)"
                      >
                        <lucide-icon [img]="icons.Check" [size]="14" aria-hidden="true" />
                      </button>
                      <button
                        type="button"
                        class="btn btn--ghost btn--icon btn--sm"
                        aria-label="Annuler"
                        (click)="renaming.set(null)"
                      >
                        <lucide-icon [img]="icons.X" [size]="14" aria-hidden="true" />
                      </button>
                    } @else {
                      <span>{{ s.name }}</span>
                      <button
                        type="button"
                        class="btn btn--ghost btn--icon btn--sm"
                        [attr.aria-label]="'Renommer ' + s.name"
                        (click)="startRename(s.id, s.name)"
                      >
                        <lucide-icon [img]="icons.Pencil" [size]="12" aria-hidden="true" />
                      </button>
                      <button
                        type="button"
                        class="btn btn--ghost btn--icon btn--sm"
                        [attr.aria-label]="(s.active ? 'Désactiver ' : 'Réactiver ') + s.name"
                        [attr.title]="s.active ? 'Désactiver' : 'Réactiver'"
                        (click)="toggleSub(s.id, !s.active)"
                      >
                        <lucide-icon [img]="icons.Power" [size]="12" aria-hidden="true" />
                      </button>
                    }
                  </li>
                }
                <li class="sub sub--add">
                  <input
                    class="input sub__input"
                    [attr.aria-label]="'Nouvelle sous-catégorie de ' + c.name"
                    placeholder="Nouvelle sous-catégorie"
                    maxlength="80"
                    #newSub
                    (keydown.enter)="addSub(c, newSub.value); newSub.value = ''"
                  />
                  <button
                    type="button"
                    class="btn btn--ghost btn--icon btn--sm"
                    [attr.aria-label]="'Ajouter une sous-catégorie à ' + c.name"
                    (click)="addSub(c, newSub.value); newSub.value = ''"
                  >
                    <lucide-icon [img]="icons.Plus" [size]="14" aria-hidden="true" />
                  </button>
                </li>
              </ul>
            </li>
          }
        </ul>
      } @else {
        <nx-skeleton height="64px" [lines]="5" />
      }
    </section>
  `,
  styleUrl: './settings.scss',
})
export class CategoriesComponent {
  private readonly api = inject(SettingsService);
  private readonly dialog = inject(Dialog);
  private readonly confirmService = inject(ConfirmService);
  private readonly toasts = inject(ToastService);

  protected readonly icons = { Check, Pencil, Plus, Power, X };

  private readonly version = signal(0);
  private readonly state = loadState(this.version, () => this.api.categories());
  /** Keeps the previous list while a refresh loads: no skeleton flash after each change. */
  protected readonly categories = linkedSignal<LoadState<Category[]>, Category[] | null>({
    source: this.state,
    computation: (state, previous) => dataOf(state) ?? previous?.value ?? null,
  });

  protected readonly renaming = signal<number | null>(null);
  protected renameValue = '';

  protected edit(category: Category | null): void {
    this.dialog
      .open<Category, Category | null>(CategoryDialogComponent, {
        data: category,
        ariaLabelledBy: 'nx-category-title',
        panelClass: 'nx-dialog-panel',
        backdropClass: 'nx-backdrop',
      })
      .closed.subscribe((saved) => {
        if (saved) {
          this.toasts.success(category ? 'Catégorie modifiée.' : 'Catégorie créée.');
          this.refresh();
        }
      });
  }

  protected toggleCategory(c: Category): void {
    if (!c.active) {
      this.run(this.api.setCategoryActive(c.id, true), 'Catégorie réactivée.');
      return;
    }
    this.confirmService
      .confirm({
        title: `Désactiver « ${c.name} »`,
        message:
          'Elle ne sera plus proposée pour les nouveaux tickets. Impossible tant que des tickets en cours l’utilisent.',
        confirmLabel: 'Désactiver',
        tone: 'danger',
      })
      .subscribe((ok) => {
        if (ok) {
          this.run(this.api.setCategoryActive(c.id, false), 'Catégorie désactivée.');
        }
      });
  }

  protected addSub(c: Category, name: string): void {
    if (name.trim()) {
      this.run(this.api.addSubcategory(c.id, name.trim()), 'Sous-catégorie ajoutée.');
    }
  }

  protected startRename(id: number, name: string): void {
    this.renameValue = name;
    this.renaming.set(id);
  }

  protected rename(id: number): void {
    if (!this.renameValue.trim()) {
      return;
    }
    this.renaming.set(null);
    this.run(this.api.renameSubcategory(id, this.renameValue.trim()), 'Sous-catégorie renommée.');
  }

  protected toggleSub(id: number, active: boolean): void {
    this.run(
      this.api.setSubcategoryActive(id, active),
      active ? 'Sous-catégorie réactivée.' : 'Sous-catégorie désactivée.',
    );
  }

  private run(call: Observable<unknown>, success: string): void {
    call.subscribe({
      next: () => {
        this.toasts.success(success);
        this.refresh();
      },
      error: (error: unknown) => this.toasts.error(toApiProblem(error).detail),
    });
  }

  private refresh(): void {
    this.version.update((n) => n + 1);
  }
}
