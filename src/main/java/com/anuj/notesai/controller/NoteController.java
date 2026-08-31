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

/**
 * NoteController — REST endpoints for creating, viewing, and deleting notes.
 *
 * ============================================================
 *  HOW DO WE KNOW WHICH USER IS MAKING THE REQUEST?
 * ============================================================
 * After the JwtAuthenticationFilter validates the token and sets the
 * SecurityContext, we can access the authenticated user in two ways:
 *
 * Option 1: SecurityContextHolder (manual, verbose)
 *   Authentication auth = SecurityContextHolder.getContext().getAuthentication();
 *   User user = (User) auth.getPrincipal();
 *
 * Option 2: @AuthenticationPrincipal (annotation, clean) ← WE USE THIS
 *   public ResponseEntity<?> method(@AuthenticationPrincipal User user) {
 *       // 'user' is automatically injected by Spring Security
 *   }
 *
 * @AuthenticationPrincipal extracts the principal (our User entity) from
 * the SecurityContext and injects it directly as a method parameter.
 * Much cleaner than the manual approach!
 *
 * INTERVIEW TIP: "I use @AuthenticationPrincipal to inject the authenticated
 * user directly into controller methods. This is cleaner than manually
 * accessing the SecurityContext, and Spring handles the injection automatically."
 *
 * ============================================================
 *  HTTP STATUS CODES USED
 * ============================================================
 *   200 OK         → successful GET, login
 *   201 Created    → successful POST (note created)
 *   204 No Content → successful DELETE (nothing to return)
 *   400 Bad Request → validation failed
 *   401 Unauthorized → missing/invalid JWT
 *   404 Not Found  → note doesn't exist or belongs to another user
 *   500 Internal Server Error → unexpected errors
 */
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

    // ================================================================
    //  CREATE NOTE FROM TEXT (Stage 1 + 2)
    // ================================================================

    /**
     * Create a new note by summarizing pasted text.
     *
     * Request:  POST /api/notes
     * Header:   Authorization: Bearer <JWT_TOKEN>
     * Body:     { "text": "Spring Boot is a framework that..." }
     * Response: { "id": 1, "summary": "...", "keyPoints": [...], ... }
     * Status:   201 Created
     *
     * @param user    the authenticated user (injected by Spring Security)
     * @param request the request body containing the text to summarize
     */
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

    // ================================================================
    //  CREATE NOTE FROM PDF UPLOAD (Stage 3)
    // ================================================================

    /**
     * Create a new note by uploading a PDF file.
     *
     * Request:  POST /api/notes/upload
     * Header:   Authorization: Bearer <JWT_TOKEN>
     * Body:     multipart/form-data with a "file" field containing the PDF
     * Response: { "id": 2, "summary": "...", "keyPoints": [...], ... }
     * Status:   201 Created
     *
     * In Postman:
     *   - Method: POST
     *   - URL: http://localhost:8080/api/notes/upload
     *   - Body tab → form-data
     *   - Key: "file" (type: File) → select your PDF
     *   - Headers tab → Authorization: Bearer <your_token>
     *
     * consumes = MediaType.MULTIPART_FORM_DATA_VALUE tells Spring this
     * endpoint accepts file uploads (not JSON).
     */
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

    // ================================================================
    //  LIST ALL NOTES (Stage 2)
    // ================================================================

    /**
     * Get all notes belonging to the authenticated user.
     *
     * Request:  GET /api/notes
     * Header:   Authorization: Bearer <JWT_TOKEN>
     * Response: [{ "id": 1, ... }, { "id": 2, ... }]
     * Status:   200 OK
     *
     * Notes are ordered by creation date (newest first).
     * Only returns notes belonging to the authenticated user.
     */
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

    // ================================================================
    //  GET ONE NOTE (Stage 2)
    // ================================================================

    /**
     * Get a specific note by its ID.
     *
     * Request:  GET /api/notes/5
     * Header:   Authorization: Bearer <JWT_TOKEN>
     * Response: { "id": 5, "summary": "...", "keyPoints": [...], ... }
     * Status:   200 OK  or  404 Not Found
     *
     * @PathVariable extracts the {id} from the URL path.
     * The note must belong to the authenticated user (checked in NoteService).
     */
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

    // ================================================================
    //  DELETE NOTE (Stage 2)
    // ================================================================

    /**
     * Delete a note by its ID.
     *
     * Request:  DELETE /api/notes/5
     * Header:   Authorization: Bearer <JWT_TOKEN>
     * Response: (empty body)
     * Status:   204 No Content  or  404 Not Found
     *
     * 204 No Content is the standard HTTP status for successful deletion
     * where there's nothing meaningful to return in the response body.
     */
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
        return ResponseEntity.noContent().build(); // 204 No Content
    }

    // ================================================================
    //  ASK A QUESTION ABOUT A NOTE (Stage 4 — Stretch Goal)
    // ================================================================

    /**
     * Ask a question about a specific note using AI.
     *
     * Request:  POST /api/notes/5/ask
     * Header:   Authorization: Bearer <JWT_TOKEN>
     * Body:     { "question": "What are the main benefits mentioned?" }
     * Response: { "noteId": 5, "question": "...", "answer": "..." }
     * Status:   200 OK
     *
     * The AI uses the note's original text as context to answer the question.
     * This is a basic form of RAG (Retrieval-Augmented Generation) without
     * needing a vector database — we simply pass the full note text.
     *
     * INTERVIEW TIP: "The ask endpoint demonstrates a simple RAG pattern.
     * I pass the note's text as context along with the question, so the AI
     * answers based only on that specific note — no hallucination."
     */
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
        // First, verify the note exists and belongs to this user
        Note note = noteRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Note not found with id: " + id
                ));

        // Send the note's text + question to the AI
        String answer = aiSummaryService.answerQuestion(
                note.getOriginalText(),
                request.getQuestion()
        );

        AskResponse response = new AskResponse(note.getId(), request.getQuestion(), answer);
        return ResponseEntity.ok(response);
    }
}
