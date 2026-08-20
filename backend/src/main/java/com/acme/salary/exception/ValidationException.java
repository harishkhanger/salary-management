package com.acme.salary.exception;

/** Business-rule validation failure -> 400 with code VALIDATION. */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
