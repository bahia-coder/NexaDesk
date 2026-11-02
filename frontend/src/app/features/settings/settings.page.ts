import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { Router } from '@angular/router';

import { CategoriesComponent } from './categories.component';
import { PrioritiesComponent } from './priorities.component';
import { SlaCalendarComponent } from './sla-calendar.component';
import { SlaRulesComponent } from './sla-rules.component';

type Tab = 'sla' | 'categories' | 'priorities';
const TABS: { id: Tab; label: string }[] = [
  { id: 'sla', label: 'SLA et calendrier' },
  { id: 'categories', label: 'Catégories' },
  { id: 'priorities', label: 'Priorités' },
];

/** Admin settings; the open tab is in the URL (?tab=). */
@Component({
  selector: 'nx-settings-page',
  imports: [SlaRulesComponent, SlaCalendarComponent, CategoriesComponent, PrioritiesComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <h1 class="page-title">Paramètres</h1>
      <div class="tabs" role="tablist" aria-label="Paramètres">
        @for (t of tabs; track t.id) {
          <button
            type="button"
            role="tab"
            class="tab"
            [attr.aria-selected]="current() === t.id"
            (click)="open(t.id)"
          >
            {{ t.label }}
          </button>
        }
      </div>
      <div role="tabpanel" class="stack">
        @switch (current()) {
          @case ('sla') {
            <nx-sla-rules />
            <nx-sla-calendar />
          }
          @case ('categories') {
            <nx-categories />
          }
          @case ('priorities') {
            <nx-priorities />
          }
        }
      </div>
    </div>
  `,
  styleUrl: './settings.scss',
})
export class SettingsPage {
  private readonly router = inject(Router);

  readonly tab = input<string>();

  protected readonly tabs = TABS;
  protected readonly current = computed<Tab>(
    () => TABS.find((t) => t.id === this.tab())?.id ?? 'sla',
  );

  protected open(tab: Tab): void {
    void this.router.navigate([], { queryParams: { tab: tab === 'sla' ? null : tab } });
  }
}
