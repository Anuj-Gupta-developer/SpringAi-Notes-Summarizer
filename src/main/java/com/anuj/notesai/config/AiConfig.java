package com.anuj.notesai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AiConfig — Configures the Spring AI ChatClient bean.
 *
 * ============================================================
 *  WHAT IS ChatClient IN SPRING AI?
 * ============================================================
 * ChatClient is Spring AI's high-level abstraction for interacting with
 * AI language models. Think of it like RestTemplate/WebClient but for AI APIs.
 *
 * It uses a fluent builder pattern:
 *   chatClient.prompt()           → start building a prompt
 *             .system("...")      → set a system message (instructions for the AI)
 *             .user("...")        → set the user message (the actual question/text)
 *             .call()             → send the request to the AI model
 *             .content()          → get the response as a String
 *
 * HOW THE GROQ CONNECTION WORKS:
 *   1. Spring AI's auto-configuration reads application.properties:
 *      - spring.ai.openai.base-url → Groq's API endpoint
 *      - spring.ai.openai.api-key  → your Groq API key
 *      - spring.ai.openai.chat.options.model → llama-3.3-70b-versatile
 *   2. It creates a ChatModel bean configured to talk to Groq
 *   3. It creates a ChatClient.Builder bean that uses this ChatModel
 *   4. We use the Builder here to create our ChatClient with a default system prompt
 *
 * WHY A DEFAULT SYSTEM PROMPT?
 * The system prompt tells the AI what role it should play and how to behave.
 * By setting it here in the config, every call to the AI automatically includes
 * these instructions — we don't need to repeat them in every service method.
 *
 * INTERVIEW TIP: "Spring AI auto-configures a ChatClient.Builder from my
 * application.properties. I use it to create a ChatClient bean with a default
 * system prompt that instructs the AI to act as a professional summarizer.
 * Since Groq's API is OpenAI-compatible, I just changed the base URL —
 * no code changes needed."
 */
@Configuration
public class AiConfig {

    /**
     * Create a ChatClient bean with a default system prompt.
     *
     * The system prompt is like giving the AI its "job description" before
     * every conversation. It shapes how the AI responds to all requests.
     *
     * @param builder auto-configured by Spring AI based on application.properties
     * @return a fully configured ChatClient ready to use
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        You are a professional text summarizer and note-taking assistant.
                        Your job is to analyze text provided by the user and produce:
                        1. A clear, concise summary (2-4 sentences)
                        2. A list of 3-7 key points (bullet-point style)
                        
                        Guidelines:
                        - Be accurate — don't add information that isn't in the original text
                        - Be concise — summaries should be significantly shorter than the original
                        - Use simple, clear language
                        - Key points should capture the most important ideas
                        - If the text is very short, still produce a summary and at least 3 key points
                        """)
                .build();
    }
}
