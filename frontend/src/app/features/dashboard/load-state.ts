import { Signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Observable, catchError, map, of, startWith, switchMap } from 'rxjs';

export type LoadState<T> =
  { status: 'loading' } | { status: 'error' } | { status: 'ready'; data: T };

/**
 * Loads data each time `params` changes (and cancels the previous request): loading, error or ready.
 * Must be called in an injection context (a component field initializer).
 */
export function loadState<P, T>(
  params: Signal<P>,
  load: (params: P) => Observable<T>,
): Signal<LoadState<T>> {
  return toSignal(
    toObservable(params).pipe(
      switchMap((value) =>
        load(value).pipe(
          map((data): LoadState<T> => ({ status: 'ready', data })),
          catchError(() => of<LoadState<T>>({ status: 'error' })),
          startWith<LoadState<T>>({ status: 'loading' }),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } },
  );
}

export function dataOf<T>(state: LoadState<T>): T | null {
  return state.status === 'ready' ? state.data : null;
}
