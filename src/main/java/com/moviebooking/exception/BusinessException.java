package com.moviebooking.exception;

/** Thrown when a business rule is violated, e.g. seat already taken (HTTP 400). */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
