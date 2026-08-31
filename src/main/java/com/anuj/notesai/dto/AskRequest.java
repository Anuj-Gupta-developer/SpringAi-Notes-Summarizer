package com.anuj.notesai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for asking a question about a specific note. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AskRequest {

    @NotBlank(message = "Question is required")
    private String question;
}
