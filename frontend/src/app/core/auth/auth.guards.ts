import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, Router } from '@angular/router';

import { Role } from '../models/user.models';
import { AuthService } from './auth.service';

/**
 * Guards only shape navigation (no point showing a page the API will refuse); the API enforces permissions.
 * The session is restored at startup (app initializer), so they can read it synchronously.
 */

/** Signed-in users only; others go to the sign-in page and come back afterwards. */
export const authGuard: CanMatchFn = (_route, segments) => {
  const auth = inject(AuthService);
  if (auth.isAuthenticated()) {
    return true;
  }
  const returnUrl = '/' + segments.map((segment) => segment.path).join('/');
  return inject(Router).createUrlTree(['/login'], {
    queryParams: returnUrl === '/' ? {} : { returnUrl },
  });
};

/** The sign-in page is pointless once signed in. */
export const guestGuard: CanMatchFn = () =>
  inject(AuthService).isAuthenticated() ? inject(Router).createUrlTree(['/']) : true;

/** A temporary password must be changed before anything else (the API refuses all the rest anyway). */
export const passwordChangedGuard: CanActivateFn = () =>
  inject(AuthService).user()?.mustChangePassword
    ? inject(Router).createUrlTree(['/change-password'])
    : true;

/** Pages reserved to some roles; others land on their home page. */
export function roleGuard(...roles: Role[]): CanMatchFn {
  return () => {
    const role = inject(AuthService).role();
    return role !== null && roles.includes(role) ? true : inject(Router).createUrlTree(['/']);
  };
}

/** Where to go after signing in: the requested page if it is an internal path, home otherwise. */
export function safeReturnUrl(returnUrl: string | null | undefined): string {
  return returnUrl && returnUrl.startsWith('/') && !returnUrl.startsWith('//') ? returnUrl : '/';
}
