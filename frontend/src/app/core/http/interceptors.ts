import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { ToastService } from '../services/toast.service';
import { HANDLES_ERRORS, toApiProblem } from './api-error';

/** Endpoints that work without (or issue) an access token. */
const PUBLIC_AUTH = [
  '/api/auth/login',
  '/api/auth/refresh',
  '/api/auth/logout',
  '/api/auth/demo-accounts',
];

function isApi(request: HttpRequest<unknown>): boolean {
  return request.url.startsWith('/api/');
}

function isPublicAuth(request: HttpRequest<unknown>): boolean {
  return PUBLIC_AUTH.some((path) => request.url.startsWith(path));
}

function withToken(request: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return request.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}

/**
 * Adds the access token to API calls. On a 401 (token expired), renews it once (a single refresh shared by all
 * concurrent requests) and replays the request; if the session cannot be renewed, back to the sign-in page.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token();
  if (!isApi(request) || isPublicAuth(request) || !token) {
    return next(request);
  }
  return next(withToken(request, token)).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        catchError((refreshError: unknown) => {
          auth.expire(router.url);
          return throwError(() => refreshError);
        }),
        switchMap((fresh) => next(withToken(request, fresh))),
      );
    }),
  );
};

/**
 * Errors nobody handles locally become a toast: network and server errors with the trace id to quote, access
 * refused, and business conflicts with their message. Validation errors (400) belong to the form that sent them.
 * A temporary password blocks the API until changed: the user is sent to the change form.
 */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const toasts = inject(ToastService);
  const router = inject(Router);
  return next(request).pipe(
    catchError((error: unknown) => {
      const problem = toApiProblem(error);
      if (problem.code === 'PASSWORD_CHANGE_REQUIRED') {
        void router.navigateByUrl('/change-password');
      } else if (!request.context.get(HANDLES_ERRORS) && problem.status !== 401) {
        if (problem.status === 0 || problem.status >= 500) {
          toasts.error(
            problem.detail,
            problem.traceId ? `Référence : ${problem.traceId}` : undefined,
          );
        } else if (problem.status !== 400) {
          toasts.error(problem.detail);
        }
      }
      return throwError(() => error);
    }),
  );
};
