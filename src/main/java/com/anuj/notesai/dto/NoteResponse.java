package com.anuj.notesai.dto;

import com.anuj.notesai.entity.Note;
import com.anuj.notesai.entity.SourceType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

// DTO so we don't expose User's passwordHash in API responses
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteResponse {

    private Long id;
    private String originalText;
    private String summary;
    private List<String> keyPoints;
    private SourceType sourceType;
    private LocalDateTime createdAt;

    public static NoteResponse fromEntity(Note note) {
        NoteResponse response = new NoteResponse();
        response.setId(note.getId());
        response.setOriginalText(note.getOriginalText());
        response.setSummary(note.getSummary());
        response.setKeyPoints(note.getKeyPoints());
        response.setSourceType(note.getSourceType());
        response.setCreatedAt(note.getCreatedAt());
        return response;
    }
}
