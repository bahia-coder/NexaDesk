import { BreakpointObserver } from '@angular/cdk/layout';
import { Injectable, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';

export type LayoutMode = 'mobile' | 'tablet' | 'desktop';

const STORAGE_KEY = 'nexadesk.sidebar-collapsed';
const MOBILE = '(max-width: 639.98px)';
const TABLET = '(min-width: 640px) and (max-width: 1023.98px)';

/**
 * Sidebar behaviour per screen size: a drawer on mobile, icons only on tablets, expanded (collapsible, remembered)
 * on desktops.
 */
@Injectable({ providedIn: 'root' })
export class LayoutService {
  private readonly breakpoints = inject(BreakpointObserver);

  readonly mode = toSignal(
    this.breakpoints
      .observe([MOBILE, TABLET])
      .pipe(
        map((state): LayoutMode =>
          state.breakpoints[MOBILE] ? 'mobile' : state.breakpoints[TABLET] ? 'tablet' : 'desktop',
        ),
      ),
    { initialValue: 'desktop' as LayoutMode },
  );

  private readonly collapsedOnDesktop = signal(readCollapsed());
  /** Mobile drawer; closed on every navigation. */
  readonly mobileOpen = signal(false);

  /** Icons only: always on tablets, by choice on desktops. */
  readonly compact = computed(
    () => this.mode() === 'tablet' || (this.mode() === 'desktop' && this.collapsedOnDesktop()),
  );

  toggleSidebar(): void {
    if (this.mode() === 'mobile') {
      this.mobileOpen.update((open) => !open);
      return;
    }
    const collapsed = !this.collapsedOnDesktop();
    this.collapsedOnDesktop.set(collapsed);
    try {
      localStorage.setItem(STORAGE_KEY, String(collapsed));
    } catch {
      // Storage unavailable: the choice lasts for this session only.
    }
  }

  closeMobile(): void {
    this.mobileOpen.set(false);
  }
}

function readCollapsed(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'true';
  } catch {
    return false;
  }
}
