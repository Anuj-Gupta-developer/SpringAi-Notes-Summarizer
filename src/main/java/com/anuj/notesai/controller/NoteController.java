package com.anuj.notesai.controller;

import com.anuj.notesai.dto.*;
import com.anuj.notesai.entity.Note;
import com.anuj.notesai.entity.User;
import com.anuj.notesai.exception.ResourceNotFoundException;
import com.anuj.notesai.repository.NoteRepository;
import com.anuj.notesai.service.AiSummaryService;
import com.anuj.notesai.service.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/notes")
@Tag(name = "Notes", description = "Create, view, and manage AI-summarized notes")
public class NoteController {

    private final NoteService noteService;
    private final NoteRepository noteRepository;
    private final AiSummaryService aiSummaryService;

    public NoteController(
            NoteService noteService,
            NoteRepository noteRepository,
            AiSummaryService aiSummaryService
    ) {
        this.noteService = noteService;
        this.noteRepository = noteRepository;
        this.aiSummaryService = aiSummaryService;
    }

    // POST /api/notes — create note from pasted text
    @PostMapping
    @Operation(
            summary = "Create note from text",
            description = "Submit text to be summarized by AI. The note is saved to your account."
    )
    public ResponseEntity<NoteResponse> createNote(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody NoteRequest request
    ) {
        NoteResponse response = noteService.createFromText(user.getId(), request.getText());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // POST /api/notes/upload — create note from PDF file
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Create note from PDF upload",
            description = "Upload a PDF file. Text is extracted, summarized by AI, and saved as a note."
    )
    public ResponseEntity<NoteResponse> uploadPdf(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file
    ) {
        NoteResponse response = noteService.createFromPdf(user.getId(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /api/notes — list all notes for the logged-in user
    @GetMapping
    @Operation(
            summary = "List all notes",
            description = "Returns all notes belonging to the authenticated user, newest first"
    )
    public ResponseEntity<List<NoteResponse>> getAllNotes(
            @AuthenticationPrincipal User user
    ) {
        List<NoteResponse> notes = noteService.getUserNotes(user.getId());
        return ResponseEntity.ok(notes);
    }

    // GET /api/notes/{id}
    @GetMapping("/{id}")
    @Operation(
            summary = "Get note by ID",
            description = "Returns a specific note. Must belong to the authenticated user."
    )
    public ResponseEntity<NoteResponse> getNoteById(
            @AuthenticationPrincipal User user,
            @PathVariable Long id
    ) {
        NoteResponse note = noteService.getNoteById(id, user.getId());
        return ResponseEntity.ok(note);
    }

    // DELETE /api/notes/{id}
    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete note",
            description = "Deletes a specific note. Must belong to the authenticated user."
    )
    public ResponseEntity<Void> deleteNote(
            @AuthenticationPrincipal User user,
            @PathVariable Long id
    ) {
        noteService.deleteNote(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    // POST /api/notes/{id}/ask — ask a question about a specific note
    // the AI uses the note's text as context to answer
    @PostMapping("/{id}/ask")
    @Operation(
            summary = "Ask a question about a note",
            description = "AI answers your question using the note's text as context (simple RAG)"
    )
    public ResponseEntity<AskResponse> askQuestion(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody AskRequest request
    ) {
        Note note = noteRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Note not found with id: " + id
                ));

        String answer = aiSummaryService.answerQuestion(
                note.getOriginalText(),
                request.getQuestion()
        );

        AskResponse response = new AskResponse(note.getId(), request.getQuestion(), answer);
        return ResponseEntity.ok(response);
    }
}
