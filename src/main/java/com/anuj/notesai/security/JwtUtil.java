package com.anuj.notesai.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JwtUtil — Utility class for creating and validating JSON Web Tokens (JWTs).
 *
 * ============================================================
 *  WHAT IS A JWT? (Interview-ready explanation)
 * ============================================================
 * A JWT is a compact, URL-safe token that carries information (called "claims")
 * between two parties. It has three parts separated by dots:
 *
 *   HEADER.PAYLOAD.SIGNATURE
 *
 * Example: eyJhbGciOi...  .  eyJzdWIiOi...  .  SflKxwRJSMeK...
 *           (header)            (payload)          (signature)
 *
 * HEADER:    {"alg": "HS256", "typ": "JWT"}
 *            → Which signing algorithm was used
 *
 * PAYLOAD:   {"sub": "john", "iat": 1234567890, "exp": 1234654290}
 *            → The actual data (claims). "sub" = subject (username),
 *              "iat" = issued at, "exp" = expiration time
 *
 * SIGNATURE: HMACSHA256(base64(header) + "." + base64(payload), secretKey)
 *            → Ensures the token hasn't been tampered with
 *
 * ============================================================
 *  HOW JWT AUTH WORKS IN THIS PROJECT (full flow)
 * ============================================================
 *
 * 1. USER REGISTERS:
 *    POST /api/auth/register → password is BCrypt-hashed and saved
 *
 * 2. USER LOGS IN:
 *    POST /api/auth/login → server verifies password, generates JWT
 *    Response: { "token": "eyJhbGciOi..." }
 *
 * 3. USER ACCESSES PROTECTED ENDPOINT:
 *    GET /api/notes
 *    Header: Authorization: Bearer eyJhbGciOi...
 *
 *    → JwtAuthenticationFilter intercepts the request
 *    → Extracts the token from the header
 *    → Calls JwtUtil.extractUsername() to get the username from the token
 *    → Loads the user from the database
 *    → Calls JwtUtil.isTokenValid() to verify the token
 *    → If valid, sets the authentication in SecurityContext
 *    → Request proceeds to the controller
 *
 * WHY STATELESS?
 * Unlike session-based auth (where the server stores session data), JWT is
 * stateless — the server doesn't store any session info. All the information
 * needed to authenticate is IN the token itself. This makes the server
 * horizontally scalable (any server instance can verify the token).
 *
 * INTERVIEW TIP: "JWT is stateless — the token contains all the information
 * needed for authentication. The server doesn't store sessions, which makes
 * the application horizontally scalable."
 */
@Component
public class JwtUtil {

    /**
     * The secret key used to sign tokens.
     * Read from application.properties: jwt.secret
     *
     * @Value injects the value from the properties file into this field.
     * The secret MUST be Base64-encoded and at least 256 bits long for HS256.
     */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * Token expiration time in milliseconds.
     * Read from application.properties: jwt.expiration
     * Default: 86400000 ms = 24 hours
     */
    @Value("${jwt.expiration}")
    private long expirationMs;

    // ================================================================
    //  TOKEN GENERATION
    // ================================================================

    /**
     * Generate a JWT token for the given user.
     *
     * @param userDetails the authenticated user (implements UserDetails)
     * @return a signed JWT token string
     *
     * The token will contain:
     *   - sub (subject): the username
     *   - iat (issued at): current timestamp
     *   - exp (expiration): current time + expirationMs
     *   - signature: HMAC-SHA256 signed with our secret key
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        return generateToken(extraClaims, userDetails);
    }

    /**
     * Generate a JWT token with optional extra claims.
     *
     * Extra claims could include things like user role, email, etc.
     * For this project, we don't add extras, but the method is here
     * for extensibility.
     *
     * HOW Jwts.builder() WORKS:
     *   1. .claims(extraClaims)  → sets any additional key-value pairs in the payload
     *   2. .subject(username)    → sets the "sub" claim (who this token is for)
     *   3. .issuedAt(now)        → sets the "iat" claim (when the token was created)
     *   4. .expiration(expiry)   → sets the "exp" claim (when the token expires)
     *   5. .signWith(key)        → signs the token with our HMAC-SHA key
     *   6. .compact()            → builds the final token string
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    // ================================================================
    //  TOKEN VALIDATION
    // ================================================================

    /**
     * Validate a JWT token against the given user details.
     *
     * A token is valid if:
     *   1. The username in the token matches the user
     *   2. The token hasn't expired
     *
     * @param token the JWT token string
     * @param userDetails the user to validate against
     * @return true if the token is valid for this user
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    /**
     * Check if the token's expiration date is before the current time.
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ================================================================
    //  CLAIM EXTRACTION
    // ================================================================

    /**
     * Extract the username (subject claim) from the token.
     *
     * The "subject" claim is the standard JWT claim for identifying
     * who the token belongs to. We store the username here.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract the expiration date from the token.
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Generic method to extract any claim from the token using a resolver function.
     *
     * This uses Java's Function interface:
     *   - Claims::getSubject  → extracts the subject
     *   - Claims::getExpiration → extracts the expiration
     *   - claims -> claims.get("customField") → extracts a custom field
     *
     * @param token the JWT token string
     * @param claimsResolver a function that takes Claims and returns the desired value
     * @return the extracted claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Parse the token and extract ALL claims from its payload.
     *
     * This is where the actual JWT verification happens:
     *   1. Jwts.parser() creates a parser
     *   2. .verifyWith(key) sets the key to verify the signature
     *   3. .build() builds the parser
     *   4. .parseSignedClaims(token) parses and verifies the token
     *
     * If the token is invalid (wrong signature, expired, malformed),
     * this method throws an exception (caught by the JwtAuthenticationFilter).
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ================================================================
    //  SIGNING KEY
    // ================================================================

    /**
     * Create the HMAC-SHA signing key from the Base64-encoded secret.
     *
     * Steps:
     *   1. Decode the Base64 secret string into raw bytes
     *   2. Create an HMAC-SHA key from those bytes
     *
     * WHY Base64?
     * The secret key needs to be at least 256 bits (32 bytes) for HS256.
     * Base64 encoding is a safe way to represent binary data as a string
     * in application.properties.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
