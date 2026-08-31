package com.anuj.notesai.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SwaggerConfig — Configures Swagger/OpenAPI documentation for the REST API.
 *
 * ============================================================
 *  WHAT IS SWAGGER / OPENAPI?
 * ============================================================
 * Swagger (now called OpenAPI) auto-generates interactive API documentation
 * from your @RestController annotations.
 *
 * After starting the app, visit: http://localhost:8080/swagger-ui.html
 *
 * You'll see:
 *   - All your endpoints listed by controller
 *   - Request/response schemas for each endpoint
 *   - A "Try it out" button to test endpoints directly from the browser
 *   - JWT authentication support (paste your token once, test all endpoints)
 *
 * ============================================================
 *  JWT CONFIGURATION IN SWAGGER
 * ============================================================
 * Since our API uses JWT authentication, we need to tell Swagger about it.
 * The SecurityScheme below adds an "Authorize" button to the Swagger UI
 * where you can paste your JWT token. Once authorized, Swagger automatically
 * adds the "Authorization: Bearer <token>" header to all test requests.
 *
 * INTERVIEW TIP: "I configured Swagger with JWT support so that during demos,
 * I can authenticate once and test all endpoints directly from the browser
 * without Postman."
 */
@Configuration
public class SwaggerConfig {

    /**
     * Configure the OpenAPI documentation metadata and JWT security scheme.
     */
    @Bean
    public OpenAPI customOpenAPI() {
        // Define the security scheme name (used to reference it in endpoints)
        final String securitySchemeName = "Bearer Authentication";

        return new OpenAPI()
                // ========== API METADATA ==========
                .info(new Info()
                        .title("AI Notes Summarizer API")
                        .version("1.0.0")
                        .description("""
                                A Spring Boot REST API for summarizing text and PDFs using AI.
                                
                                **Features:**
                                - User registration and JWT-based authentication
                                - Text and PDF note summarization via Spring AI + Groq
                                - Per-user note management (CRUD)
                                - Ask questions about saved notes (AI-powered Q&A)
                                
                                **Authentication:**
                                1. Register a new account using `/api/auth/register`
                                2. Login using `/api/auth/login` to receive a JWT token
                                3. Click the "Authorize" button above and enter: `Bearer <your-token>`
                                4. All subsequent requests will include the token automatically
                                """)
                        .contact(new Contact()
                                .name("Anuj")
                                .url("https://github.com/anuj"))
                )
                // ========== GLOBAL SECURITY REQUIREMENT ==========
                // This tells Swagger "all endpoints require this security scheme"
                // (individual endpoints can override this with @SecurityRequirements)
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                // ========== SECURITY SCHEME DEFINITION ==========
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)  // HTTP-based auth
                                        .scheme("bearer")                // Bearer token scheme
                                        .bearerFormat("JWT")             // Token format is JWT
                                        .description("Enter your JWT token. Get it from POST /api/auth/login")
                        )
                );
    }
}
