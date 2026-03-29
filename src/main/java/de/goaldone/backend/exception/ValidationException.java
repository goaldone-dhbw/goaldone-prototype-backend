package de.goaldone.backend.exception;

import lombok.Getter;

@Getter
public class ValidationException extends RuntimeException {
    private final String field;
    private final String detail;

    public ValidationException(String field, String detail) {
        super(field + ": " + detail);
        this.field = field;
        this.detail = detail;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
