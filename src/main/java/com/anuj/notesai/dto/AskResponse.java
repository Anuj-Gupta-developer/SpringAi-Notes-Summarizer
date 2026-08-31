package com.anuj.notesai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response containing the AI's answer to a question about a note. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AskResponse {

    private Long noteId;
    private String question;
    private String answer;
}
