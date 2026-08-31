package com.anuj.notesai.security;

import com.anuj.notesai.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthenticationFilter — Intercepts every HTTP request to check for a valid JWT.
 *
 * ============================================================
 *  WHERE DOES THIS FIT IN THE SPRING SECURITY ARCHITECTURE?
 * ============================================================
 *
 * When a request comes in, Spring Security processes it through a FILTER CHAIN:
 *
 *   HTTP Request
 *       │
 *       ▼
 *   ┌──────────────────────────┐
 *   │  CorsFilter              │  ← Handles CORS headers
 *   ├──────────────────────────┤
 *   │  JwtAuthenticationFilter │  ← OUR FILTER (this class!)
 *   │  (runs BEFORE the        │     Extracts JWT, validates it,
 *   │   default auth filter)   │     and sets the SecurityContext
 *   ├──────────────────────────┤
 *   │  UsernamePassword        │  ← Default Spring Security filter
 *   │  AuthenticationFilter    │     (we bypass this — we use JWT instead)
 *   ├──────────────────────────┤
 *   │  ExceptionTranslation    │  ← Converts auth exceptions to HTTP 401/403
 *   │  Filter                  │
 *   ├──────────────────────────┤
 *   │  AuthorizationFilter     │  ← Checks if the authenticated user
 *   │                          │     has permission for this endpoint
 *   └──────────────────────────┘
 *       │
 *       ▼
 *   Your @RestController method
 *
 * ============================================================
 *  WHY OncePerRequestFilter?
 * ============================================================
 * We extend OncePerRequestFilter (not just Filter) to guarantee
 * this filter runs exactly ONCE per request, even if the request
 * is forwarded internally (which can cause regular filters to run twice).
 *
 * ============================================================
 *  WHAT THIS FILTER DOES (step by step)
 * ============================================================
 *   1. Extract the "Authorization" header from the request
 *   2. Check if it starts with "Bearer " (if not, skip — it's not a JWT request)
 *   3. Extract the token (everything after "Bearer ")
 *   4. Extract the username from the token using JwtUtil
 *   5. Load the user from the database
 *   6. Validate the token (correct user + not expired)
 *   7. If valid: create an Authentication object and set it in the SecurityContext
 *   8. If invalid: do nothing (the request will be rejected by the AuthorizationFilter)
 *   9. Pass the request to the next filter in the chain
 *
 * INTERVIEW TIP: "My JWT filter extends OncePerRequestFilter to guarantee
 * single execution per request. It extracts the token from the Authorization
 * header, validates it, and if valid, sets the SecurityContext so downstream
 * filters and controllers know who the authenticated user is."
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    /**
     * Constructor injection — Spring automatically provides JwtUtil and UserRepository.
     *
     * WHY CONSTRUCTOR INJECTION (not @Autowired on fields)?
     *   1. Makes dependencies explicit and visible
     *   2. Fields can be final (immutable) — safer in multi-threaded environments
     *   3. Easier to test — you can pass mock dependencies in unit tests
     *   4. Recommended by the Spring team
     *
     * INTERVIEW TIP: "I use constructor injection because it makes dependencies
     * explicit, allows fields to be final, and is the Spring-recommended approach."
     */
    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    /**
     * The core filter method — runs for every HTTP request.
     *
     * @param request     the incoming HTTP request
     * @param response    the outgoing HTTP response
     * @param filterChain the chain of remaining filters to execute
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // STEP 1: Get the Authorization header
        // Expected format: "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWI..."
        final String authHeader = request.getHeader("Authorization");

        // STEP 2: If there's no header or it doesn't start with "Bearer ",
        // this is not a JWT-authenticated request. Skip this filter and
        // let the request continue through the filter chain.
        // (Public endpoints like /api/auth/login don't send a token)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // STEP 3: Extract the token (remove the "Bearer " prefix)
        // "Bearer eyJhbGci..." → "eyJhbGci..."
        final String jwt = authHeader.substring(7);

        try {
            // STEP 4: Extract the username from the token's "sub" claim
            final String username = jwtUtil.extractUsername(jwt);

            // STEP 5: Only proceed if we got a username AND there's no existing authentication
            // SecurityContextHolder.getContext().getAuthentication() == null means
            // no other filter has already authenticated this request
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // STEP 6: Load the full user details from the database
                // We need the user details to validate the token and set up the SecurityContext
                UserDetails userDetails = userRepository.findByUsername(username)
                        .orElse(null);

                // STEP 7: Validate the token — check username match + expiration
                if (userDetails != null && jwtUtil.isTokenValid(jwt, userDetails)) {

                    // STEP 8: Create an Authentication object
                    // UsernamePasswordAuthenticationToken is Spring Security's standard
                    // implementation. The 3-arg constructor marks it as "authenticated".
                    //
                    // Parameters:
                    //   - principal:    the user details (who is authenticated)
                    //   - credentials:  null (we don't need the password anymore — token is verified)
                    //   - authorities:  the user's roles/permissions
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    // Attach request-specific details (IP address, session ID, etc.)
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    // STEP 9: Set the authentication in the SecurityContext
                    // This is the CRITICAL step — it tells Spring Security
                    // "this request is authenticated as user X with roles Y"
                    //
                    // From this point, any code that calls
                    // SecurityContextHolder.getContext().getAuthentication()
                    // will get this authentication object.
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // If anything goes wrong (expired token, invalid signature, etc.),
            // we simply don't set the authentication. The request will continue
            // as unauthenticated, and the AuthorizationFilter will reject it
            // with a 401 Unauthorized response if the endpoint requires auth.
            //
            // We intentionally catch all exceptions here because we don't want
            // a bad token to crash the filter chain — we just want to reject it.
            logger.error("JWT authentication failed: " + e.getMessage());
        }

        // STEP 10: Continue the filter chain — pass the request to the next filter
        // If authentication was set, the request proceeds as authenticated.
        // If not, the request proceeds as anonymous (and will be rejected
        // by the AuthorizationFilter for protected endpoints).
        filterChain.doFilter(request, response);
    }
}
