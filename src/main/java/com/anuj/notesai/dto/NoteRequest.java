package com.anuj.notesai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for creating a note from pasted text. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteRequest {

    @NotBlank(message = "Text content is required")
    @Size(min = 50, message = "Text must be at least 50 characters long for a meaningful summary")
    private String text;
}
