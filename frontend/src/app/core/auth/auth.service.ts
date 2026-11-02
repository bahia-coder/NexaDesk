import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, firstValueFrom, map, of, share, tap } from 'rxjs';

import { HANDLES_ERRORS } from '../http/api-error';
import { AuthResponse, CurrentUser, DemoAccount } from './auth.models';

/**
 * Session of the signed-in user.
 *
 * <p>The access token (15 min) lives only in memory: unreadable by other tabs and gone on reload, so an XSS
 * cannot steal a long-lived credential from storage. The refresh token is an httpOnly cookie the browser sends
 * to /api/auth only; on reload, {@link restoreSession} trades it for a new access token.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly currentUser = signal<CurrentUser | null>(null);
  private accessToken: string | null = null;
  /** Refresh in flight: concurrent 401s all wait for this one request (the server rotates the token). */
  private refreshing: Observable<string> | null = null;

  readonly user = this.currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUser() !== null);
  readonly role = computed(() => this.currentUser()?.role ?? null);

  token(): string | null {
    return this.accessToken;
  }

  login(email: string, password: string): Observable<CurrentUser> {
    return this.http
      .post<AuthResponse>(
        '/api/auth/login',
        { email, password },
        { context: new HttpContext().set(HANDLES_ERRORS, true) },
      )
      .pipe(map((response) => this.apply(response)));
  }

  /** New access token from the refresh cookie; one request at a time whatever the number of callers. */
  refresh(): Observable<string> {
    if (!this.refreshing) {
      this.refreshing = this.http
        .post<AuthResponse>('/api/auth/refresh', null, {
          context: new HttpContext().set(HANDLES_ERRORS, true),
        })
        .pipe(
          map((response) => {
            this.apply(response);
            return response.accessToken;
          }),
          finalize(() => (this.refreshing = null)),
          share(),
        );
    }
    return this.refreshing;
  }

  /** At startup: resumes the session if the refresh cookie is still valid. Never fails. */
  restoreSession(): Promise<void> {
    return firstValueFrom(
      this.refresh().pipe(
        map(() => undefined),
        catchError(() => of(undefined)),
      ),
    );
  }

  /** Revokes the session on the server (best effort), then forgets it locally. */
  logout(): Observable<void> {
    return this.http
      .post<void>('/api/auth/logout', null, {
        context: new HttpContext().set(HANDLES_ERRORS, true),
      })
      .pipe(
        catchError(() => of(undefined)),
        tap(() => {
          this.clear();
          void this.router.navigateByUrl('/login');
        }),
        map(() => undefined),
      );
  }

  /** The session could not be renewed: back to the sign-in page, then to where the user was. */
  expire(returnUrl: string): void {
    this.clear();
    void this.router.navigate(['/login'], {
      queryParams: { returnUrl, expired: 1 },
    });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<CurrentUser> {
    return this.http
      .put<AuthResponse>(
        '/api/profile/password',
        { currentPassword, newPassword },
        { context: new HttpContext().set(HANDLES_ERRORS, true) },
      )
      .pipe(map((response) => this.apply(response)));
  }

  /** Empty when the backend runs without the "demo" profile. */
  demoAccounts(): Observable<DemoAccount[]> {
    return this.http
      .get<DemoAccount[]>('/api/auth/demo-accounts', {
        context: new HttpContext().set(HANDLES_ERRORS, true),
      })
      .pipe(catchError(() => of([])));
  }

  private apply(response: AuthResponse): CurrentUser {
    this.accessToken = response.accessToken;
    this.currentUser.set(response.user);
    return response.user;
  }

  private clear(): void {
    this.accessToken = null;
    this.currentUser.set(null);
  }
}
