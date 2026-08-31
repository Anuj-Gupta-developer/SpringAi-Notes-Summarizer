package com.anuj.notesai.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * GlobalExceptionHandler — Catches exceptions thrown by any controller and
 * returns consistent, user-friendly error responses.
 *
 * ============================================================
 *  WHAT IS @RestControllerAdvice?
 * ============================================================
 * It's a combination of:
 *   @ControllerAdvice → applies to ALL controllers in the app
 *   @ResponseBody     → converts return values to JSON automatically
 *
 * Without this, unhandled exceptions would produce ugly HTML error pages
 * (Spring's default "Whitelabel Error Page"). With this, every error
 * returns a clean JSON response.
 *
 * HOW IT WORKS:
 *   1. A controller method throws an exception (e.g., ResourceNotFoundException)
 *   2. Spring looks for an @ExceptionHandler method that handles that exception type
 *   3. The handler method creates an ErrorResponse and returns it with the right HTTP status
 *
 * ORDER OF HANDLERS:
 * Spring picks the most specific handler first. If no specific handler exists,
 * the generic Exception handler at the bottom catches it.
 *
 * INTERVIEW TIP: "I use @RestControllerAdvice for centralized exception handling.
 * Each exception type is mapped to an appropriate HTTP status code, and all errors
 * return a consistent JSON response with status, message, and timestamp."
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle validation errors from @Valid on request bodies.
     *
     * When a DTO has @NotBlank, @Size, etc. and the client sends invalid data,
     * Spring throws MethodArgumentNotValidException.
     *
     * We extract all field error messages and join them into one string.
     *
     * Example response:
     * {
     *   "status": 400,
     *   "message": "Username is required; Password must be between 6 and 100 characters",
     *   "timestamp": "2025-01-15T10:30:00"
     * }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex
    ) {
        // Collect all field validation error messages into a single string
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),   // 400
                errors,
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle "resource not found" errors (e.g., note with given ID doesn't exist).
     * Returns HTTP 404 Not Found.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex
    ) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),     // 404
                ex.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle "duplicate resource" errors (e.g., username already exists).
     * Returns HTTP 409 Conflict.
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex
    ) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.CONFLICT.value(),      // 409
                ex.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handle bad login credentials (wrong username or password).
     * Returns HTTP 401 Unauthorized.
     *
     * Spring Security throws BadCredentialsException when
     * AuthenticationManager.authenticate() fails.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex
    ) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),  // 401
                "Invalid username or password",
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Handle invalid arguments (e.g., non-PDF file upload, empty file, blank extracted text).
     * Returns HTTP 400 Bad Request.
     *
     * NoteService throws IllegalArgumentException when:
     *   - The uploaded file is empty
     *   - The uploaded file is not a PDF
     *   - The PDF contains no extractable text
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex
    ) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),    // 400
                ex.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle file upload size exceeded (PDF larger than 10MB).
     * Returns HTTP 413 Payload Too Large.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex
    ) {
        ErrorResponse response = new ErrorResponse(
                HttpStatus.PAYLOAD_TOO_LARGE.value(),  // 413
                "File size exceeds the maximum allowed size of 10MB",
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    /**
     * Catch-all handler for any unexpected exceptions.
     * Returns HTTP 500 Internal Server Error.
     *
     * This prevents sensitive stack traces from leaking to the client.
     * In production, you'd also log the full exception for debugging.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        // Log the full stack trace for debugging (visible in server console)
        ex.printStackTrace();

        ErrorResponse response = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),  // 500
                "An unexpected error occurred. Please try again later.",
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
