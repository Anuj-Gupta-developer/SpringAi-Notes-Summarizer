package com.anuj.notesai.service;

import com.anuj.notesai.dto.AuthResponse;
import com.anuj.notesai.dto.LoginRequest;
import com.anuj.notesai.dto.RegisterRequest;
import com.anuj.notesai.entity.User;
import com.anuj.notesai.exception.DuplicateResourceException;
import com.anuj.notesai.repository.UserRepository;
import com.anuj.notesai.security.JwtUtil;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * AuthService — Handles user registration, login, and user details loading.
 *
 * ============================================================
 *  WHY DOES THIS IMPLEMENT UserDetailsService?
 * ============================================================
 * Spring Security needs a way to load user details from the database
 * during authentication. The {@link UserDetailsService} interface has
 * one method: loadUserByUsername(String username).
 *
 * By implementing it here, Spring Security auto-discovers this bean
 * and uses it when the AuthenticationManager needs to authenticate a user.
 *
 * FLOW:
 *   1. User sends POST /api/auth/login with {username, password}
 *   2. Controller calls AuthService.login()
 *   3. login() calls AuthenticationManager.authenticate()
 *   4. AuthenticationManager calls our loadUserByUsername() to find the user
 *   5. AuthenticationManager uses PasswordEncoder to verify the password
 *   6. If valid, we generate a JWT token and return it
 *
 * ============================================================
 *  CIRCULAR DEPENDENCY — WHY @Lazy IS NEEDED HERE
 * ============================================================
 * There's a circular dependency:
 *   AuthService needs AuthenticationManager
 *   → AuthenticationManager needs UserDetailsService (to load users)
 *   → UserDetailsService IS AuthService (this class!)
 *   → AuthService needs AuthenticationManager → CYCLE!
 *
 * @Lazy breaks this cycle by telling Spring:
 *   "Don't create the real AuthenticationManager yet. Give me a proxy.
 *    Create the real one only when I first call a method on it."
 *
 * This works because AuthenticationManager is only used in the login()
 * method, which isn't called during startup — only when a user logs in.
 *
 * INTERVIEW TIP: "I encountered a circular dependency between AuthService
 * and AuthenticationManager. I used @Lazy to defer the AuthenticationManager
 * creation, which is safe because it's only needed at login time, not startup."
 *
 * ============================================================
 *  SERVICE LAYER ROLE
 * ============================================================
 * The Service layer contains BUSINESS LOGIC:
 *   - Validation rules (is username taken?)
 *   - Data transformations (hash password, generate JWT)
 *   - Orchestration (coordinate between repos, utils)
 *
 * The Controller layer only handles HTTP concerns (request/response mapping).
 * This separation makes the code testable and maintainable.
 *
 * INTERVIEW TIP: "My AuthService implements UserDetailsService so Spring Security
 * can load users from MySQL during authentication. The service handles business
 * logic like checking for duplicate usernames and generating JWT tokens, while
 * the controller only handles HTTP mapping."
 */
@Service
public class   AuthService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    /**
     * Constructor injection — all dependencies are provided by Spring.
     *
     * @Lazy on AuthenticationManager breaks the circular dependency
     * (see class-level Javadoc for full explanation).
     */
    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            @Lazy AuthenticationManager authenticationManager
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
    }

    // ================================================================
    //  USER REGISTRATION
    // ================================================================

    /**
     * Register a new user.
     *
     * Steps:
     *   1. Check if username is already taken → throw 409 if yes
     *   2. Create a new User entity
     *   3. Hash the password with BCrypt
     *   4. Save to the database
     *   5. Generate and return a JWT token (so user is logged in immediately)
     *
     * @param request the registration request containing username and password
     * @return AuthResponse containing a JWT token
     * @throws DuplicateResourceException if username already exists
     */
    public AuthResponse register(RegisterRequest request) {
        // Step 1: Check for duplicate username
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException(
                    "Username '" + request.getUsername() + "' is already taken"
            );
        }

        // Step 2 & 3: Create user with hashed password
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword())); // BCrypt hash
        user.setRole("ROLE_USER");

        // Step 4: Save to MySQL (JPA handles the INSERT query)
        userRepository.save(user);

        // Step 5: Generate JWT so user is logged in immediately after registration
        String token = jwtUtil.generateToken(user);
        return new AuthResponse(token);
    }

    // ================================================================
    //  USER LOGIN
    // ================================================================

    /**
     * Authenticate a user and return a JWT token.
     *
     * Steps:
     *   1. Use AuthenticationManager to verify credentials
     *      (it calls our loadUserByUsername + PasswordEncoder internally)
     *   2. If authentication fails → Spring throws BadCredentialsException
     *      (caught by GlobalExceptionHandler → 401 Unauthorized)
     *   3. If authentication succeeds → load the user and generate a JWT
     *
     * @param request the login request containing username and password
     * @return AuthResponse containing a JWT token
     */
    public AuthResponse login(LoginRequest request) {
        // Step 1: Authenticate — this is where Spring Security verifies the password
        // Behind the scenes:
        //   a) AuthenticationManager calls loadUserByUsername(request.getUsername())
        //   b) Gets the stored BCrypt hash from the returned UserDetails
        //   c) Calls passwordEncoder.matches(request.getPassword(), storedHash)
        //   d) If match → returns Authentication object
        //   e) If no match → throws BadCredentialsException
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        // Step 2: Get the authenticated user details
        // The principal is the UserDetails object (our User entity)
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        // Step 3: Generate and return the JWT token
        String token = jwtUtil.generateToken(userDetails);
        return new AuthResponse(token);
    }

    // ================================================================
    //  UserDetailsService IMPLEMENTATION
    // ================================================================

    /**
     * Load a user by their username — required by Spring Security.
     *
     * This method is called automatically by Spring Security during:
     *   1. Login (via AuthenticationManager)
     *   2. JWT validation (we also call the repo directly in JwtAuthenticationFilter
     *      for efficiency, but this method is needed for the AuthenticationManager)
     *
     * @param username the username to search for
     * @return the UserDetails object (our User entity)
     * @throws UsernameNotFoundException if the user doesn't exist
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with username: " + username
                ));
    }
}
