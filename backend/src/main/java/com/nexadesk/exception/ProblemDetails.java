package com.nexadesk.exception;

import com.nexadesk.common.web.TraceIdFilter;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Builds the RFC 9457 error bodies used by the MVC exception handler and the security handlers. */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ProblemDetail of(ErrorCode code, String detail) {
        return of(code.getStatus(), code, detail);
    }

    public static ProblemDetail of(HttpStatusCode status, ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        enrich(problem, code);
        return problem;
    }

    public static void enrich(ProblemDetail problem, ErrorCode code) {
        problem.setProperty("code", code.name());
        problem.setProperty("timestamp", Instant.now());
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
    }
}
