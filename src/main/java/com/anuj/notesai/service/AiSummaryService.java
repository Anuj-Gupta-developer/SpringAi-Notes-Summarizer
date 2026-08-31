package com.anuj.notesai.service;

import com.anuj.notesai.dto.AiSummaryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * AiSummaryService — Handles all interactions with the AI model (Groq/LLaMA).
 *
 * ============================================================
 *  HOW SPRING AI's ChatClient WORKS
 * ============================================================
 *
 * The ChatClient uses a fluent API (builder pattern) to construct AI requests:
 *
 *   chatClient.prompt()        → Start building a new prompt
 *             .user("text")    → Set the user message (what you want the AI to do)
 *             .call()          → Send the request to the AI model
 *             .entity(Type)    → Parse the response into a Java object
 *
 * Under the hood, when you call .entity(AiSummaryResult.class):
 *   1. Spring AI generates a JSON schema from the AiSummaryResult record
 *   2. It adds this schema to the prompt: "Respond in JSON with this format: ..."
 *   3. The AI responds with JSON matching the schema
 *   4. Spring AI parses the JSON into an AiSummaryResult object
 *
 * This is called "structured output" — the AI's response is automatically
 * converted into a typed Java object. No manual JSON parsing needed!
 *
 * ============================================================
 *  PROMPT ENGINEERING
 * ============================================================
 * The quality of AI output depends heavily on how you write the prompt.
 *
 * Our approach:
 *   - System prompt (set in AiConfig): defines the AI's role and guidelines
 *   - User prompt (set here): provides the specific text to summarize
 *
 * The system prompt stays the same for every request.
 * The user prompt changes with each summarization request.
 *
 * INTERVIEW TIP: "I separated the system prompt (AI's role/behavior) from
 * the user prompt (specific request). The system prompt is configured once
 * in AiConfig, and each service method provides the user prompt with the
 * actual text to process."
 */
@Service
public class AiSummaryService {

    private static final Logger logger = LoggerFactory.getLogger(AiSummaryService.class);

    private final ChatClient chatClient;

    public AiSummaryService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Summarize the given text using the AI model.
     *
     * @param text the original text to summarize
     * @return an AiSummaryResult containing the summary and key points
     *
     * HOW THIS WORKS:
     *   1. We send the text to the AI with instructions to summarize
     *   2. Spring AI adds JSON schema instructions automatically (from the record)
     *   3. The AI responds with structured JSON
     *   4. Spring AI parses it into an AiSummaryResult object
     *
     * If the AI call fails (network error, rate limit, etc.), we throw
     * a RuntimeException which gets caught by the GlobalExceptionHandler
     * and returned as a 500 error.
     */
    public AiSummaryResult summarize(String text) {
        logger.info("Sending text to AI for summarization ({} characters)", text.length());

        try {
            // Build the prompt and call the AI model
            // The system prompt (from AiConfig) is automatically included
            AiSummaryResult result = chatClient.prompt()
                    .user("Please summarize the following text and extract key points:\n\n" + text)
                    .call()
                    .entity(AiSummaryResult.class);

            logger.info("AI summarization complete. Key points: {}", result.keyPoints().size());
            return result;

        } catch (Exception e) {
            logger.error("AI summarization failed: {}", e.getMessage());
            throw new RuntimeException(
                    "Failed to generate summary. Please try again. Error: " + e.getMessage(), e
            );
        }
    }

    /**
     * Answer a question about a note using the AI model (Stage 4 — stretch goal).
     *
     * This sends the note's original text as context along with the user's question.
     * The AI answers based ONLY on the provided text — no external knowledge.
     *
     * This is a basic form of RAG (Retrieval-Augmented Generation) without
     * a vector database — we simply pass the full note text as context.
     *
     * @param noteText the original text of the note (used as context)
     * @param question the user's question about the note
     * @return the AI's answer as a String
     */
    public String answerQuestion(String noteText, String question) {
        logger.info("Answering question about note ({} chars context)", noteText.length());

        try {
            String answer = chatClient.prompt()
                    .system("""
                            You are a helpful assistant. Answer the user's question based ONLY
                            on the provided context text. If the answer cannot be found in the
                            context, say "I cannot find the answer to this question in the note."
                            Do not make up information.
                            """)
                    .user("Context:\n" + noteText + "\n\nQuestion: " + question)
                    .call()
                    .content(); // Returns plain text (not structured output)

            logger.info("Question answered successfully");
            return answer;

        } catch (Exception e) {
            logger.error("AI question answering failed: {}", e.getMessage());
            throw new RuntimeException(
                    "Failed to answer question. Please try again. Error: " + e.getMessage(), e
            );
        }
    }
}
