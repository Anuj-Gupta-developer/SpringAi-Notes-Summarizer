package com.anuj.notesai.service;

import com.anuj.notesai.dto.AiSummaryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

// talks to the AI model (Groq) for summarization and Q&A
@Service
public class AiSummaryService {

    private static final Logger logger = LoggerFactory.getLogger(AiSummaryService.class);

    private final ChatClient chatClient;

    public AiSummaryService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    // sends text to the AI and gets back a structured summary + key points
    public AiSummaryResult summarize(String text) {
        logger.info("Sending text to AI for summarization ({} characters)", text.length());

        try {
            AiSummaryResult result = chatClient.prompt()
                    .user("Please summarize the following text and extract key points:\n\n" + text)
                    .call()
                    .entity(AiSummaryResult.class); // auto-parses JSON response into our record

            logger.info("AI summarization complete. Key points: {}", result.keyPoints().size());
            return result;

        } catch (Exception e) {
            logger.error("AI summarization failed: {}", e.getMessage());
            throw new RuntimeException(
                    "Failed to generate summary. Please try again. Error: " + e.getMessage(), e
            );
        }
    }

    // answers a question about a note using the note's text as context
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
                    .content();

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
