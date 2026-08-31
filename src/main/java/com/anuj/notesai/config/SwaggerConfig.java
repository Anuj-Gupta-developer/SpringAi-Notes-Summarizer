package com.anuj.notesai.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// sets up Swagger UI with JWT auth support
// after starting the app, go to http://localhost:9090/swagger-ui.html
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "Bearer Authentication";

        return new OpenAPI()
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
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT token. Get it from POST /api/auth/login")
                        )
                );
    }
}
