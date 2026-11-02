import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  ActivatedRoute,
  ActivatedRouteSnapshot,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import {
  Hexagon,
  LucideAngularModule,
  Menu,
  Moon,
  PanelLeftClose,
  PanelLeftOpen,
  Sun,
  X,
} from 'lucide-angular';
import { filter, map } from 'rxjs';

import { Role } from '../../core/models/user.models';
import { navigationFor } from '../../core/navigation/navigation';
import { LayoutService } from '../../core/services/layout.service';
import { ThemeService } from '../../core/services/theme.service';

/**
 * Authenticated layout shared by all roles: sidebar (navigation of the role), header, and the page.
 * Pages are child routes; the sidebar lists the role's items whose child route exists.
 */
@Component({
  selector: 'nx-app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LucideAngularModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
  host: { '(document:keydown.escape)': 'layout.closeMobile()' },
})
export class AppShellComponent {
  protected readonly layout = inject(LayoutService);
  protected readonly theme = inject(ThemeService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /** Role whose navigation is shown. */
  readonly role = input<Role | null>(null);

  protected readonly icons = { Hexagon, Menu, Moon, PanelLeftClose, PanelLeftOpen, Sun, X };

  /** Links to pages not built yet are shown (disabled) in development only. */
  private readonly showPlanned = typeof ngDevMode === 'undefined' || !!ngDevMode;
  private readonly routedPaths = new Set(
    (this.route.routeConfig?.children ?? []).map((child) => child.path?.split('/')[0] ?? ''),
  );

  protected readonly sections = computed(() => {
    const role = this.role();
    return role ? navigationFor(role, this.routedPaths, this.showPlanned) : [];
  });

  protected readonly pageTitle = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => deepestTitle(this.router.routerState.snapshot.root)),
    ),
    { initialValue: deepestTitle(this.router.routerState.snapshot.root) },
  );

  constructor() {
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe(() => this.layout.closeMobile());
  }
}

/** Title of the deepest active route, without the "NexaDesk · " prefix of the browser tab. */
function deepestTitle(snapshot: ActivatedRouteSnapshot): string {
  let current: ActivatedRouteSnapshot | null = snapshot;
  let title = '';
  while (current) {
    if (typeof current.title === 'string') {
      title = current.title;
    }
    current = current.firstChild;
  }
  return title.replace(/^NexaDesk · /, '');
}
