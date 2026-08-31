package com.anuj.notesai.service;

import com.anuj.notesai.dto.AiSummaryResult;
import com.anuj.notesai.dto.NoteResponse;
import com.anuj.notesai.entity.Note;
import com.anuj.notesai.entity.SourceType;
import com.anuj.notesai.entity.User;
import com.anuj.notesai.exception.ResourceNotFoundException;
import com.anuj.notesai.repository.NoteRepository;
import com.anuj.notesai.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * NoteService — Business logic for creating, reading, and deleting notes.
 *
 * ============================================================
 *  WHAT DOES @Transactional DO?
 * ============================================================
 * Database operations that modify data (INSERT, UPDATE, DELETE) should
 * be wrapped in a transaction. A transaction ensures that if something
 * goes wrong mid-operation, ALL changes are rolled back (undo).
 *
 * Example: If saving a note succeeds but saving key points fails,
 * @Transactional rolls back the note save too — no half-saved data.
 *
 * Read-only operations use @Transactional(readOnly = true) as a
 * performance hint to Hibernate (it can skip dirty-checking).
 *
 * INTERVIEW TIP: "I use @Transactional on write operations to ensure
 * atomicity — if any part of the operation fails, everything is rolled back.
 * Read operations use readOnly=true as a Hibernate optimization."
 *
 * ============================================================
 *  SERVICE METHOD NAMING CONVENTION
 * ============================================================
 * - createFromText()  → creates a note from pasted text
 * - createFromPdf()   → creates a note from an uploaded PDF
 * - getUserNotes()    → lists all notes for a user
 * - getNoteById()     → gets one note (with ownership check)
 * - deleteNote()      → deletes one note (with ownership check)
 */
@Service
public class NoteService {

    private static final Logger logger = LoggerFactory.getLogger(NoteService.class);

    /**
     * Maximum number of characters to send to the AI model.
     * AI models have a token/context-window limit. Sending too much text
     * causes HTTP 400 "Please reduce the length of the messages".
     * ~12,000 chars ≈ ~3,000 tokens — well within Groq's free-tier limits
     * while still providing enough content for a meaningful summary.
     */
    private static final int MAX_CHARS_FOR_AI = 8_000;

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final AiSummaryService aiSummaryService;

    public NoteService(
            NoteRepository noteRepository,
            UserRepository userRepository,
            AiSummaryService aiSummaryService
    ) {
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.aiSummaryService = aiSummaryService;
    }

    // ================================================================
    //  CREATE NOTE FROM PASTED TEXT (Stage 1 + 2)
    // ================================================================

    /**
     * Create a new note from pasted text.
     *
     * Flow:
     *   1. Load the user from the database
     *   2. Send the text to the AI for summarization
     *   3. Build a Note entity with the original text + AI output
     *   4. Save to the database
     *   5. Return the note as a DTO
     *
     * @param userId the ID of the authenticated user (from JWT)
     * @param text   the original text to summarize
     * @return NoteResponse containing the saved note with summary + key points
     */
    @Transactional
    public NoteResponse createFromText(Long userId, String text) {
        logger.info("Creating note from text for user {}", userId);

        // Step 1: Load the user (needed for the @ManyToOne relationship)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Step 2: Truncate text if it exceeds the AI model's context window limit,
        // then call the AI to generate summary + key points
        String textForAi = truncateForAi(text);
        AiSummaryResult aiResult = aiSummaryService.summarize(textForAi);

        // Step 3: Build the Note entity (store the full original text, not the truncated version)
        Note note = new Note();
        note.setUser(user);
        note.setOriginalText(text);
        note.setSummary(aiResult.summary());
        note.setKeyPoints(aiResult.keyPoints());
        note.setSourceType(SourceType.PASTED_TEXT);
        note.setCreatedAt(LocalDateTime.now());

        // Step 4: Save to MySQL
        Note savedNote = noteRepository.save(note);
        logger.info("Note saved with id {} for user {}", savedNote.getId(), userId);

        // Step 5: Convert entity → DTO and return
        return NoteResponse.fromEntity(savedNote);
    }

    // ================================================================
    //  CREATE NOTE FROM PDF UPLOAD (Stage 3)
    // ================================================================

    /**
     * Create a new note from an uploaded PDF file.
     *
     * Flow:
     *   1. Validate the file (must be a PDF, must not be empty)
     *   2. Extract text from the PDF using Spring AI's PagePdfDocumentReader
     *   3. Send extracted text to the AI for summarization
     *   4. Save the note with source type PDF_UPLOAD
     *
     * ============================================================
     *  HOW PagePdfDocumentReader WORKS
     * ============================================================
     * PagePdfDocumentReader is part of Spring AI's document reader module.
     * It uses Apache PDFBox under the hood to:
     *   1. Parse the PDF file structure
     *   2. Extract text from each page
     *   3. Return a List<Document> where each Document represents one page
     *
     * We combine all pages' text into a single String for summarization.
     *
     * INTERVIEW TIP: "For PDF text extraction, I used Spring AI's
     * PagePdfDocumentReader instead of writing my own PDFBox code.
     * It returns a Document per page, and I join them for summarization."
     *
     * @param userId the ID of the authenticated user
     * @param file   the uploaded PDF file (as MultipartFile from the HTTP request)
     * @return NoteResponse containing the saved note
     */
    @Transactional
    public NoteResponse createFromPdf(Long userId, MultipartFile file) {
        logger.info("Creating note from PDF upload for user {}", userId);

        // Step 1: Validate the file
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new IllegalArgumentException("Only PDF files are allowed. Received: " + contentType);
        }

        // Step 2: Extract text from the PDF
        String extractedText = extractTextFromPdf(file);
        if (extractedText.isBlank()) {
            throw new IllegalArgumentException(
                    "Could not extract any text from the PDF. The file might be image-based (scanned)."
            );
        }
        logger.info("Extracted {} characters from PDF", extractedText.length());

        // Step 3: Load user and summarize
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Truncate extracted text before sending to AI to avoid exceeding token limits
        String textForAi = truncateForAi(extractedText);
        AiSummaryResult aiResult = aiSummaryService.summarize(textForAi);

        // Step 4: Build and save the Note entity
        Note note = new Note();
        note.setUser(user);
        note.setOriginalText(extractedText);
        note.setSummary(aiResult.summary());
        note.setKeyPoints(aiResult.keyPoints());
        note.setSourceType(SourceType.PDF_UPLOAD);
        note.setCreatedAt(LocalDateTime.now());

        Note savedNote = noteRepository.save(note);
        logger.info("PDF note saved with id {} for user {}", savedNote.getId(), userId);

        return NoteResponse.fromEntity(savedNote);
    }

    /**
     * Extract text from a PDF file using Spring AI's PagePdfDocumentReader.
     *
     * @param file the uploaded MultipartFile
     * @return the extracted text (all pages combined)
     */
    private String extractTextFromPdf(MultipartFile file) {
        try {
            // Convert MultipartFile to a Spring Resource
            // MultipartFile.getResource() returns a Resource that wraps the file's bytes
            Resource pdfResource = file.getResource();

            // Create a PDF reader — this parses the PDF and extracts text per page
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource);

            // Read all pages as Document objects
            List<Document> documents = pdfReader.read();

            // Combine all pages' text into a single string, separated by newlines
            return documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n"));

        } catch (Exception e) {
            logger.error("Failed to extract text from PDF: {}", e.getMessage());
            throw new RuntimeException("Failed to read PDF file: " + e.getMessage(), e);
        }
    }

    /**
     * Truncates text to MAX_CHARS_FOR_AI characters to stay within the AI model's
     * context window. If truncated, appends a notice so the AI knows the text was cut.
     *
     * @param text the original full text
     * @return text safe to send to the AI model
     */
    private String truncateForAi(String text) {
        if (text.length() <= MAX_CHARS_FOR_AI) {
            return text;
        }
        logger.warn("Text too long ({} chars), truncating to {} chars for AI summarization",
                text.length(), MAX_CHARS_FOR_AI);
        return text.substring(0, MAX_CHARS_FOR_AI)
                + "\n\n[Note: The text was truncated due to length. Please summarize based on the above content.]";  
    }

    // ================================================================
    //  READ OPERATIONS
    // ================================================================

    /**
     * Get all notes belonging to a specific user, newest first.
     *
     * @param userId the ID of the authenticated user
     * @return list of NoteResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<NoteResponse> getUserNotes(Long userId) {
        logger.info("Fetching notes for user {}", userId);

        List<Note> notes = noteRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // Convert each Note entity to a NoteResponse DTO
        // Using Java Streams: a functional way to transform lists
        return notes.stream()
                .map(NoteResponse::fromEntity)  // Method reference: same as note -> NoteResponse.fromEntity(note)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific note by ID, ensuring it belongs to the authenticated user.
     *
     * SECURITY NOTE: We use findByIdAndUserId() instead of just findById()
     * to prevent IDOR (Insecure Direct Object Reference) attacks.
     * Even if a user guesses another user's note ID, this method won't
     * return it because the userId won't match.
     *
     * INTERVIEW TIP: "I prevent IDOR attacks by always including the userId
     * in the database query. Even if someone guesses a valid note ID,
     * they can't access it if it belongs to another user."
     *
     * @param noteId the note's ID
     * @param userId the authenticated user's ID
     * @return NoteResponse DTO
     * @throws ResourceNotFoundException if note doesn't exist or belongs to another user
     */
    @Transactional(readOnly = true)
    public NoteResponse getNoteById(Long noteId, Long userId) {
        Note note = noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Note not found with id: " + noteId
                ));
        return NoteResponse.fromEntity(note);
    }

    // ================================================================
    //  DELETE OPERATION
    // ================================================================

    /**
     * Delete a note, ensuring it belongs to the authenticated user.
     *
     * @param noteId the note's ID to delete
     * @param userId the authenticated user's ID
     * @throws ResourceNotFoundException if note doesn't exist or belongs to another user
     */
    @Transactional
    public void deleteNote(Long noteId, Long userId) {
        logger.info("Deleting note {} for user {}", noteId, userId);

        // First verify the note exists AND belongs to this user
        Note note = noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Note not found with id: " + noteId
                ));

        // Delete the note (Hibernate also deletes the key points from
        // the note_key_points table because @ElementCollection cascades by default)
        noteRepository.delete(note);
        logger.info("Note {} deleted successfully", noteId);
    }
}
