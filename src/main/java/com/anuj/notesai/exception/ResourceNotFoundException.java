package com.anuj.notesai.exception;

/**
 * Thrown when a requested resource doesn't exist or the user doesn't have access.
 * Mapped to HTTP 404 Not Found by {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
