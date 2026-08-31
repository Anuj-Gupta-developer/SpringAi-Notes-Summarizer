package com.anuj.notesai.config;

import com.anuj.notesai.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityConfig — Configures Spring Security for JWT-based stateless authentication.
 *
 * ============================================================
 *  WHAT IS @Configuration + @EnableWebSecurity?
 * ============================================================
 * @Configuration:     Tells Spring "this class defines beans" (objects managed by Spring)
 * @EnableWebSecurity: Tells Spring Security "use my custom security rules instead of defaults"
 *
 * Without this class, Spring Security's default behavior is:
 *   - All endpoints require authentication
 *   - It generates a random password printed in the console
 *   - It uses session-based (cookie) authentication
 *   - CSRF protection is enabled
 *
 * We override all of this to use JWT-based stateless authentication.
 *
 * ============================================================
 *  WHAT IS A SecurityFilterChain?
 * ============================================================
 * A SecurityFilterChain is a list of security rules that define:
 *   1. Which endpoints are public vs. protected
 *   2. How authentication works (JWT, sessions, OAuth, etc.)
 *   3. CSRF, CORS, and session management policies
 *   4. Custom filters to insert into the chain
 *
 * Think of it as a bouncer at a club with a guest list:
 *   - /api/auth/** → "You're on the guest list, come in" (public)
 *   - Everything else → "Show me your ID (JWT)" (authenticated)
 *
 * INTERVIEW TIP: "I configured Spring Security to be stateless with JWT.
 * CSRF is disabled because it's only needed for browser-based session auth.
 * My JWT filter runs before the default UsernamePasswordAuthenticationFilter
 * to intercept and validate tokens."
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /**
     * Define the security filter chain — the heart of our security configuration.
     *
     * @param http the HttpSecurity builder (fluent API for configuring security)
     * @return the built SecurityFilterChain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // ========== CSRF (Cross-Site Request Forgery) ==========
                // CSRF protection prevents malicious websites from making requests
                // on behalf of a logged-in user (via their browser cookies).
                //
                // WHY DISABLE IT?
                // CSRF is only relevant for session/cookie-based authentication.
                // Since we use JWT (sent in the Authorization header, not cookies),
                // CSRF attacks are not possible. Disabling it simplifies our API.
                //
                // INTERVIEW TIP: "CSRF is disabled because our API uses JWT tokens
                // in headers, not cookies. CSRF attacks exploit cookies, so they
                // don't apply to our authentication mechanism."
                .csrf(csrf -> csrf.disable())

                // ========== ENDPOINT AUTHORIZATION RULES ==========
                // Define which endpoints are public and which require authentication.
                //
                // Rules are evaluated in ORDER — first match wins.
                // That's why specific paths come before the catch-all.
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints — anyone can access these without a JWT
                        .requestMatchers("/api/auth/**").permitAll()

                        // Swagger UI and OpenAPI docs — public so developers can access docs
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // Everything else — requires a valid JWT token
                        .anyRequest().authenticated()
                )

                // ========== SESSION MANAGEMENT ==========
                // Set session policy to STATELESS — Spring Security will NOT create
                // or use HTTP sessions. Every request must carry its own JWT token.
                //
                // This is crucial for JWT auth. Without STATELESS, Spring might
                // create a session after the first authenticated request, and
                // subsequent requests would use the session instead of the JWT.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // ========== REGISTER OUR JWT FILTER ==========
                // Insert our JwtAuthenticationFilter BEFORE Spring's default
                // UsernamePasswordAuthenticationFilter.
                //
                // This means:
                //   1. Request comes in
                //   2. Our JWT filter runs first → extracts and validates the JWT
                //   3. If valid, sets the SecurityContext (user is authenticated)
                //   4. Spring's default filter runs → sees user is already authenticated,
                //      skips its own logic
                //   5. Request reaches the controller
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * PasswordEncoder bean — used to hash and verify passwords.
     *
     * BCrypt is the industry-standard password hashing algorithm:
     *   - One-way hash (cannot be reversed)
     *   - Built-in salt (each hash is different even for the same password)
     *   - Configurable strength (default: 10 rounds of hashing)
     *
     * Spring Security uses this bean automatically:
     *   - During registration: encoder.encode("password") → hashed password
     *   - During login: encoder.matches("password", hashedPassword) → true/false
     *
     * INTERVIEW TIP: "BCrypt adds a random salt to each password before hashing,
     * so even if two users have the same password, their hashes are different.
     * This prevents rainbow table attacks."
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthenticationManager bean — manages the authentication process.
     *
     * Spring Security uses this to:
     *   1. Receive login credentials (username + password)
     *   2. Find the user (via UserDetailsService)
     *   3. Verify the password (via PasswordEncoder)
     *   4. Return an Authentication object if successful
     *
     * We need to expose it as a bean so we can inject it into AuthService
     * for the login flow.
     *
     * AuthenticationConfiguration is auto-configured by Spring Boot and
     * already knows about our UserDetailsService and PasswordEncoder beans.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {
        return config.getAuthenticationManager();
    }
}
