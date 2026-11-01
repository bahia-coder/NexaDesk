import { Component, VERSION, inject, isDevMode, signal } from '@angular/core';
import {
  BookOpen,
  CircleAlert,
  CircleCheck,
  CircleX,
  Hexagon,
  LucideAngularModule,
  Monitor,
  Moon,
  RefreshCw,
  Server,
  Sun,
} from 'lucide-angular';
import { finalize } from 'rxjs';

import { BackendStatus, SystemHealthService } from '../../core/services/system-health.service';
import { ThemeService } from '../../core/services/theme.service';

/** Temporary Phase 2 page: proves Angular runs and reaches Spring Boot through the proxy. */
@Component({
  selector: 'nx-system-status-page',
  imports: [LucideAngularModule],
  templateUrl: './system-status.page.html',
  styleUrl: './system-status.page.scss',
})
export class SystemStatusPage {
  private readonly health = inject(SystemHealthService);
  protected readonly theme = inject(ThemeService);

  protected readonly angularVersion = VERSION.full;
  /** Swagger UI is disabled by the backend's prod profile, so the link only exists in dev. */
  protected readonly showApiDocs = isDevMode();
  protected readonly backendStatus = signal<BackendStatus | null>(null);
  protected readonly checking = signal(false);
  protected readonly lastCheck = signal<Date | null>(null);

  protected readonly icons = {
    BookOpen,
    CircleAlert,
    CircleCheck,
    CircleX,
    Hexagon,
    Monitor,
    Moon,
    RefreshCw,
    Server,
    Sun,
  };

  constructor() {
    this.check();
  }

  protected check(): void {
    this.checking.set(true);
    this.health
      .checkBackend()
      .pipe(finalize(() => this.checking.set(false)))
      .subscribe((status) => {
        this.backendStatus.set(status);
        this.lastCheck.set(new Date());
      });
  }
}
