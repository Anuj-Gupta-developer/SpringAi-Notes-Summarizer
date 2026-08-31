package com.anuj.notesai.controller;

import com.anuj.notesai.dto.AuthResponse;
import com.anuj.notesai.dto.LoginRequest;
import com.anuj.notesai.dto.RegisterRequest;
import com.anuj.notesai.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuthController — Handles user registration and login endpoints.
 *
 * ============================================================
 *  ANNOTATIONS EXPLAINED
 * ============================================================
 * @RestController = @Controller + @ResponseBody
 *   - @Controller:    marks this class as a Spring MVC controller
 *   - @ResponseBody:  tells Spring to convert return values to JSON automatically
 *   Combined: every method returns JSON (not an HTML view)
 *
 * @RequestMapping("/api/auth")
 *   Sets the base URL path for all endpoints in this controller.
 *   All methods below will be prefixed with /api/auth/...
 *
 * @Valid (on method parameters)
 *   Triggers Jakarta Bean Validation on the request body.
 *   If any @NotBlank, @Size, etc. validation fails, Spring throws
 *   MethodArgumentNotValidException (caught by our GlobalExceptionHandler).
 *
 * ============================================================
 *  SWAGGER ANNOTATIONS
 * ============================================================
 * @Tag: Groups endpoints in Swagger UI under a named section
 * @Operation: Describes what an endpoint does (shown in Swagger)
 * @SecurityRequirements(value = {}): Marks endpoint as PUBLIC in Swagger
 *   (overrides the global JWT requirement from SwaggerConfig)
 *
 * INTERVIEW TIP: "My AuthController handles registration and login.
 * Both endpoints are public (no JWT required) — configured in SecurityConfig.
 * The login endpoint returns a JWT token that clients use for subsequent requests."
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Register and login to get JWT tokens")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Register a new user.
     *
     * Request:  POST /api/auth/register
     * Body:     { "username": "john", "password": "mypassword123" }
     * Response: { "token": "eyJhbGciOi..." }
     * Status:   201 Created
     *
     * The user is automatically logged in after registration
     * (the response includes a JWT token).
     */
    @PostMapping("/register")
    @Operation(
            summary = "Register a new user",
            description = "Creates a new user account and returns a JWT token for immediate access"
    )
    @SecurityRequirements(value = {}) // This endpoint is public — no JWT needed
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        // @Valid triggers validation of @NotBlank, @Size on RegisterRequest fields
        // If validation fails, MethodArgumentNotValidException is thrown → 400 error
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Login with existing credentials.
     *
     * Request:  POST /api/auth/login
     * Body:     { "username": "john", "password": "mypassword123" }
     * Response: { "token": "eyJhbGciOi..." }
     * Status:   200 OK
     *
     * If credentials are invalid, returns 401 Unauthorized
     * (handled by GlobalExceptionHandler catching BadCredentialsException).
     */
    @PostMapping("/login")
    @Operation(
            summary = "Login",
            description = "Authenticates user credentials and returns a JWT token"
    )
    @SecurityRequirements(value = {}) // This endpoint is public — no JWT needed
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
