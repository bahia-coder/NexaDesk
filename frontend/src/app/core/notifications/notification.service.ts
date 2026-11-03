import { HttpClient } from '@angular/common/http';
import { Injectable, effect, inject, signal, untracked } from '@angular/core';
import { Observable, map, tap } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { Page } from '../models/page.models';
import { ToastService } from '../services/toast.service';
import { NotificationSocket } from './notification-socket';
import { AppNotification } from './notification.models';

/**
 * Notification centre of the signed-in user: unread count for the header badge, list, mark as read, and the
 * live feed. Connected while someone is signed in, disconnected on sign-out.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly socket = inject(NotificationSocket);
  private readonly toasts = inject(ToastService);

  private readonly unreadCount = signal(0);
  private readonly arrivalCount = signal(0);

  readonly unread = this.unreadCount.asReadonly();
  /** Bumped by each live notification: lists depending on it reload. */
  readonly arrivals = this.arrivalCount.asReadonly();
  /** Each notification pushed live (e.g. to refresh the ticket it is about). */
  readonly received$: Observable<AppNotification> = this.socket.messages$;

  constructor() {
    effect(() => {
      const signedIn = this.auth.isAuthenticated();
      untracked(() => {
        if (signedIn) {
          this.socket.start();
        } else {
          this.socket.stop();
          this.unreadCount.set(0);
        }
      });
    });
    // On each (re)connection: the count may have changed while the channel was down.
    this.socket.connected$.subscribe(() => this.refreshCount());
    this.socket.messages$.subscribe((notification) => {
      if (!notification.read) {
        this.unreadCount.update((count) => count + 1);
      }
      this.arrivalCount.update((count) => count + 1);
      if (notification.type === 'SLA_WARNING' || notification.type === 'SLA_BREACHED') {
        this.toasts.warning(notification.title, notification.message);
      } else {
        this.toasts.info(notification.title, notification.message);
      }
    });
  }

  list(unreadOnly: boolean, page: number, size: number): Observable<Page<AppNotification>> {
    return this.http.get<Page<AppNotification>>('/api/notifications', {
      params: { unreadOnly, page, size },
    });
  }

  refreshCount(): void {
    this.http
      .get<{ count: number }>('/api/notifications/unread-count')
      .subscribe((response) => this.unreadCount.set(response.count));
  }

  markRead(notification: AppNotification): Observable<AppNotification> {
    return this.http.post<AppNotification>(`/api/notifications/${notification.id}/read`, null).pipe(
      tap(() => {
        if (!notification.read) {
          this.unreadCount.update((count) => Math.max(0, count - 1));
        }
      }),
    );
  }

  markAllRead(): Observable<number> {
    return this.http.post<{ updated: number }>('/api/notifications/read-all', null).pipe(
      map((response) => response.updated),
      tap(() => this.unreadCount.set(0)),
    );
  }
}
