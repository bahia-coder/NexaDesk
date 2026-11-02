import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'error' | 'info' | 'warning';

export interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
  /** Secondary line, e.g. the trace id of a server error to quote to support. */
  detail?: string;
}

const DURATION_MS: Record<ToastKind, number> = {
  success: 4000,
  info: 4000,
  warning: 6000,
  error: 8000,
};
const MAX_VISIBLE = 4;

/** Short feedback messages, stacked bottom right. Errors stay longer. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 1;
  private readonly items = signal<Toast[]>([]);
  readonly toasts = this.items.asReadonly();

  success(message: string, detail?: string): void {
    this.show('success', message, detail);
  }

  error(message: string, detail?: string): void {
    this.show('error', message, detail);
  }

  info(message: string, detail?: string): void {
    this.show('info', message, detail);
  }

  warning(message: string, detail?: string): void {
    this.show('warning', message, detail);
  }

  dismiss(id: number): void {
    this.items.update((list) => list.filter((toast) => toast.id !== id));
  }

  private show(kind: ToastKind, message: string, detail?: string): void {
    const toast: Toast = { id: this.nextId++, kind, message, detail };
    this.items.update((list) => [...list, toast].slice(-MAX_VISIBLE));
    setTimeout(() => this.dismiss(toast.id), DURATION_MS[kind]);
  }
}
