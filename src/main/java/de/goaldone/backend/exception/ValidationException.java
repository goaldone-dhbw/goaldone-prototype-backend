package de.goaldone.backend.exception;

import lombok.Getter;

@Getter
public class ValidationException extends RuntimeException {
    private final String field;
    private final String message;

    public ValidationException(String field, String message) {
        super(field + ": " + message);
        this.field = field;
        this.message = message;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
