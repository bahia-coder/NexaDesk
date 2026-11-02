import { DestroyRef, Injectable, inject, signal } from '@angular/core';

const TICK_MS = 30_000;

/**
 * Current time as a signal, refreshed every 30 seconds. Relative times ("il y a 5 minutes") and SLA countdowns
 * read it, so they update together without one timer per component.
 */
@Injectable({ providedIn: 'root' })
export class ClockService {
  private readonly current = signal(Date.now());
  readonly now = this.current.asReadonly();

  constructor() {
    const timer = setInterval(() => this.current.set(Date.now()), TICK_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }
}
