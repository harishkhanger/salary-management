package com.acme.salary.exception;

/** Missing or failed authentication -> 401 with code UNAUTHENTICATED. */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
