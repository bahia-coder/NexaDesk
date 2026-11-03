import { Injectable, inject } from '@angular/core';
import { RxStomp } from '@stomp/rx-stomp';
import { Observable, Subject, Subscription, catchError, firstValueFrom, of } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { AppNotification } from './notification.models';

const DESTINATION = '/user/queue/notifications';

/**
 * Live channel of the signed-in user's notifications: STOMP over WebSocket at /ws.
 *
 * <ul>
 *   <li>Browsers cannot set headers on a WebSocket handshake: the access token goes in the STOMP CONNECT frame,
 *       where the API checks it.</li>
 *   <li>Reconnects on its own (network loss, API restart). The token may have expired meanwhile: after a refused
 *       CONNECT, the next attempt gets a new one from the refresh cookie first.</li>
 * </ul>
 */
@Injectable({ providedIn: 'root' })
export class NotificationSocket {
  private readonly auth = inject(AuthService);

  private client: RxStomp | null = null;
  private subscriptions: Subscription[] = [];
  private tokenRefused = false;

  private readonly received = new Subject<AppNotification>();
  private readonly opened = new Subject<void>();

  readonly messages$: Observable<AppNotification> = this.received.asObservable();
  /** Each (re)connection: what was pushed while offline has to be fetched again. */
  readonly connected$: Observable<void> = this.opened.asObservable();

  start(): void {
    if (this.client) {
      return;
    }
    const client = new RxStomp();
    client.configure({
      brokerURL: socketUrl(),
      reconnectDelay: 5000,
      beforeConnect: async (stomp) => {
        const token = await this.token();
        if (!token) {
          // Session over: the next API call sends the user back to the sign-in page.
          void stomp.deactivate();
          return;
        }
        stomp.configure({ connectHeaders: { Authorization: `Bearer ${token}` } });
      },
    });
    this.subscriptions = [
      client.connected$.subscribe(() => {
        this.tokenRefused = false;
        this.opened.next();
      }),
      client.stompErrors$.subscribe(() => (this.tokenRefused = true)),
      client.watch(DESTINATION).subscribe((message) => {
        this.received.next(JSON.parse(message.body) as AppNotification);
      }),
    ];
    this.client = client;
    client.activate();
  }

  stop(): void {
    this.subscriptions.forEach((subscription) => subscription.unsubscribe());
    this.subscriptions = [];
    void this.client?.deactivate();
    this.client = null;
    this.tokenRefused = false;
  }

  private async token(): Promise<string | null> {
    const current = this.auth.token();
    if (current && !this.tokenRefused) {
      return current;
    }
    return firstValueFrom(this.auth.refresh().pipe(catchError(() => of(null))));
  }
}

/** Same origin as the page: the dev server and Nginx both proxy /ws to the API. */
function socketUrl(): string {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
  return `${scheme}://${location.host}/ws`;
}
