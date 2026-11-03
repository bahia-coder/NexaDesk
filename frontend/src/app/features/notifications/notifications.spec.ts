import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { NotificationSocket } from '../../core/notifications/notification-socket';
import { AppNotification } from '../../core/notifications/notification.models';
import { NotificationService } from '../../core/notifications/notification.service';
import { ToastService } from '../../core/services/toast.service';
import { NotificationBellComponent } from './notification-bell.component';
import { NotificationsPage } from './notifications.page';

const NOTIFICATION: AppNotification = {
  id: 7,
  type: 'NEW_COMMENT',
  title: 'Réponse sur votre ticket INC-2026-0012',
  message: 'Karim Alaoui vous a répondu.',
  ticketId: 12,
  link: '/tickets/12',
  read: false,
  createdAt: '2026-11-03T09:00:00Z',
};

class FakeSocket {
  readonly messages$ = new Subject<AppNotification>();
  readonly connected$ = new Subject<void>();
  started = 0;
  stopped = 0;
  start(): void {
    this.started++;
  }
  stop(): void {
    this.stopped++;
  }
}

function page(content: AppNotification[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

describe('Notifications', () => {
  let http: HttpTestingController;
  let socket: FakeSocket;
  const signedIn = signal(true);

  beforeEach(() => {
    signedIn.set(true);
    socket = new FakeSocket();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: NotificationSocket, useValue: socket },
        { provide: AuthService, useValue: { isAuthenticated: signedIn } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('connects while signed in, counts live notifications and disconnects on sign-out', () => {
    const service = TestBed.inject(NotificationService);
    const toasts = TestBed.inject(ToastService);
    TestBed.tick();
    expect(socket.started).toBe(1);

    socket.connected$.next();
    http.expectOne('/api/notifications/unread-count').flush({ count: 2 });
    expect(service.unread()).toBe(2);

    socket.messages$.next(NOTIFICATION);
    expect(service.unread()).toBe(3);
    expect(service.arrivals()).toBe(1);
    expect(toasts.toasts().at(-1)).toMatchObject({ kind: 'info', message: NOTIFICATION.title });

    socket.messages$.next({ ...NOTIFICATION, id: 8, type: 'SLA_BREACHED', title: 'SLA dépassé' });
    expect(toasts.toasts().at(-1)).toMatchObject({ kind: 'warning', message: 'SLA dépassé' });

    signedIn.set(false);
    TestBed.tick();
    expect(socket.stopped).toBe(1);
    expect(service.unread()).toBe(0);
  });

  it('decrements the count only for an unread notification', () => {
    const service = TestBed.inject(NotificationService);
    socket.connected$.next();
    http.expectOne('/api/notifications/unread-count').flush({ count: 1 });

    service.markRead({ ...NOTIFICATION, read: true }).subscribe();
    http.expectOne('/api/notifications/7/read').flush({ ...NOTIFICATION, read: true });
    expect(service.unread()).toBe(1);

    service.markRead(NOTIFICATION).subscribe();
    http.expectOne('/api/notifications/7/read').flush({ ...NOTIFICATION, read: true });
    expect(service.unread()).toBe(0);
  });

  it('shows the unread count on the bell', async () => {
    const service = TestBed.inject(NotificationService);
    const fixture = TestBed.createComponent(NotificationBellComponent);
    fixture.autoDetectChanges();
    const button = (fixture.nativeElement as HTMLElement).querySelector('button')!;
    expect(button.getAttribute('aria-label')).toBe('Notifications');
    expect(button.querySelector('.bell__count')).toBeNull();

    socket.connected$.next();
    http.expectOne('/api/notifications/unread-count').flush({ count: 120 });
    await fixture.whenStable();
    expect(button.getAttribute('aria-label')).toBe('Notifications, 120 non lues');
    expect(button.querySelector('.bell__count')?.textContent?.trim()).toBe('99+');
    expect(service.unread()).toBe(120);
  });

  it('marks one notification as read in place, then all of them', async () => {
    const service = TestBed.inject(NotificationService);
    socket.connected$.next();
    http.expectOne('/api/notifications/unread-count').flush({ count: 1 });

    const fixture = TestBed.createComponent(NotificationsPage);
    fixture.autoDetectChanges();
    const list = http.expectOne((r) => r.url === '/api/notifications');
    expect(list.request.params.get('unreadOnly')).toBe('false');
    list.flush(page([NOTIFICATION]));
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.row--unread')?.textContent).toContain('Karim Alaoui vous a répondu.');

    el.querySelector<HTMLButtonElement>('[aria-label="Marquer comme lue"]')!.click();
    http.expectOne('/api/notifications/7/read').flush({ ...NOTIFICATION, read: true });
    await fixture.whenStable();
    expect(el.querySelector('.row--unread')).toBeNull();
    expect(service.unread()).toBe(0);

    // A new notification arrives live: the list reloads and "Tout marquer comme lu" becomes available.
    socket.messages$.next({ ...NOTIFICATION, id: 9 });
    await fixture.whenStable();
    http
      .expectOne((r) => r.url === '/api/notifications')
      .flush(page([{ ...NOTIFICATION, id: 9 }, { ...NOTIFICATION, read: true }]));
    await fixture.whenStable();
    const readAll = Array.from(el.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Tout marquer comme lu'),
    )!;
    expect(readAll.disabled).toBe(false);
    readAll.click();
    http.expectOne({ method: 'POST', url: '/api/notifications/read-all' }).flush({ updated: 1 });
    await fixture.whenStable();
    http.expectOne((r) => r.url === '/api/notifications').flush(page([]));
    await fixture.whenStable();
    expect(service.unread()).toBe(0);
  });
});
