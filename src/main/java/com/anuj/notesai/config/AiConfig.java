package com.anuj.notesai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// configures the ChatClient with a default system prompt for summarization
@Configuration
public class AiConfig {

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
