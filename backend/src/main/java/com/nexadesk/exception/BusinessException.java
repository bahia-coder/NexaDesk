package com.nexadesk.exception;

import lombok.Getter;

/** Expected business failure, translated by {@link GlobalExceptionHandler} into a ProblemDetail response. */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code) {
        this(code, code.getDefaultMessage());
    }

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
