package com.moviebooking.exception;

/** Thrown when an account is temporarily locked after too many failed log-in attempts (HTTP 429). */
public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException(String message) {
        super(message);
    }
}
