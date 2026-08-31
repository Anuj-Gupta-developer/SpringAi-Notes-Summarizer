package com.anuj.notesai.exception;

/**
 * Thrown when a resource already exists (e.g., duplicate username).
 * Mapped to HTTP 409 Conflict by {@link GlobalExceptionHandler}.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
