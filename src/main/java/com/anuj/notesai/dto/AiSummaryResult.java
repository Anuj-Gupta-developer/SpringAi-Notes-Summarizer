package com.anuj.notesai.dto;

import java.util.List;

/**
 * Structured output format for AI summarization responses.
 * Spring AI's ChatClient uses this record's schema to instruct the LLM
 * to return JSON matching these fields, then auto-parses the response.
 *
 * @param summary   AI-generated summary of the input text
 * @param keyPoints list of key points extracted from the text
 */
public record AiSummaryResult(
        String summary,
        List<String> keyPoints
) {
}
