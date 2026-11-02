import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ChevronLeft, ChevronRight, LucideAngularModule } from 'lucide-angular';

/** "248 tickets · 20 par page" and page navigation (0-based pages, as the API). */
@Component({
  selector: 'nx-paginator',
  imports: [LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="paginator" aria-label="Pagination">
      <span class="muted">
        {{ total() }} {{ total() > 1 ? itemsLabel() : itemLabel() }}
        @if (sizes().length > 1) {
          ·
          <label class="size">
            <select
              class="select"
              aria-label="Éléments par page"
              [value]="size()"
              (change)="sizeChange.emit(+$any($event.target).value)"
            >
              @for (option of sizes(); track option) {
                <option [value]="option" [selected]="option === size()">{{ option }}</option>
              }
            </select>
            par page
          </label>
        }
      </span>
      @if (pages() > 1) {
        <span class="pages">
          <button
            type="button"
            class="btn btn--ghost btn--icon btn--sm"
            aria-label="Page précédente"
            [disabled]="page() === 0"
            (click)="pageChange.emit(page() - 1)"
          >
            <lucide-icon [img]="icons.ChevronLeft" [size]="16" aria-hidden="true" />
          </button>
          <span aria-live="polite">Page {{ page() + 1 }} sur {{ pages() }}</span>
          <button
            type="button"
            class="btn btn--ghost btn--icon btn--sm"
            aria-label="Page suivante"
            [disabled]="page() >= pages() - 1"
            (click)="pageChange.emit(page() + 1)"
          >
            <lucide-icon [img]="icons.ChevronRight" [size]="16" aria-hidden="true" />
          </button>
        </span>
      }
    </nav>
  `,
  styles: `
    .paginator {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-3);
      font: var(--text-small);
    }

    .size {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);

      .select {
        width: auto;
        min-height: 30px;
        padding: 2px var(--space-2);
      }
    }

    .pages {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
    }
  `,
})
export class PaginatorComponent {
  readonly page = input.required<number>();
  readonly size = input.required<number>();
  readonly total = input.required<number>();
  readonly sizes = input<readonly number[]>([]);
  readonly itemLabel = input('élément');
  readonly itemsLabel = input('éléments');

  readonly pageChange = output<number>();
  readonly sizeChange = output<number>();

  protected readonly icons = { ChevronLeft, ChevronRight };
  protected readonly pages = computed(() => Math.max(1, Math.ceil(this.total() / this.size())));
}
