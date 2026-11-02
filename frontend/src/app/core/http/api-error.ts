import { HttpContextToken, HttpErrorResponse } from '@angular/common/http';

/** RFC 9457 error body returned by the API (backend: exception.ProblemDetails). */
export interface ApiProblem {
  status: number;
  /** Stable machine-readable code (backend ErrorCode), e.g. "INVALID_CREDENTIALS". */
  code: string | null;
  /** French message meant for the user. */
  detail: string;
  /** Id to quote to support; it is in the server logs. */
  traceId: string | null;
  /** Field errors of a 400 VALIDATION_FAILED. */
  errors: { field: string; message: string }[];
}

const GENERIC = 'Une erreur inattendue est survenue.';
const NETWORK = 'Le serveur est injoignable. Vérifiez votre connexion et réessayez.';

export function toApiProblem(error: unknown): ApiProblem {
  if (!(error instanceof HttpErrorResponse)) {
    return { status: 0, code: null, detail: GENERIC, traceId: null, errors: [] };
  }
  if (error.status === 0) {
    return { status: 0, code: null, detail: NETWORK, traceId: null, errors: [] };
  }
  const body = (
    typeof error.error === 'object' && error.error !== null ? error.error : {}
  ) as Record<string, unknown>;
  return {
    status: error.status,
    code: typeof body['code'] === 'string' ? body['code'] : null,
    detail: typeof body['detail'] === 'string' ? body['detail'] : GENERIC,
    traceId:
      typeof body['traceId'] === 'string' ? body['traceId'] : error.headers?.get('X-Trace-Id'),
    errors: Array.isArray(body['errors']) ? (body['errors'] as ApiProblem['errors']) : [],
  };
}

/**
 * Set on a request whose errors the caller shows itself (a form, the login page): the error interceptor then
 * stays quiet instead of adding a toast.
 */
export const HANDLES_ERRORS = new HttpContextToken<boolean>(() => false);
