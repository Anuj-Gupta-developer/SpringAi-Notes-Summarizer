package com.anuj.notesai.dto;

import java.util.List;

// Spring AI parses the LLM's JSON response into this record automatically
public record AiSummaryResult(
        String summary,
        List<String> keyPoints
) {
}
