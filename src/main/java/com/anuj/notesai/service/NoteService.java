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

@Service
public class NoteService {

    private static final Logger logger = LoggerFactory.getLogger(NoteService.class);

    // limit text sent to AI to avoid exceeding the model's context window
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

    // create a note from pasted text — sends it to AI, saves the result
    @Transactional
    public NoteResponse createFromText(Long userId, String text) {
        logger.info("Creating note from text for user {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String textForAi = truncateForAi(text);
        AiSummaryResult aiResult = aiSummaryService.summarize(textForAi);

        Note note = new Note();
        note.setUser(user);
        note.setOriginalText(text); // save full text, not truncated
        note.setSummary(aiResult.summary());
        note.setKeyPoints(aiResult.keyPoints());
        note.setSourceType(SourceType.PASTED_TEXT);
        note.setCreatedAt(LocalDateTime.now());

        Note savedNote = noteRepository.save(note);
        logger.info("Note saved with id {} for user {}", savedNote.getId(), userId);

        return NoteResponse.fromEntity(savedNote);
    }

    // create a note from a PDF — extract text first, then summarize
    @Transactional
    public NoteResponse createFromPdf(Long userId, MultipartFile file) {
        logger.info("Creating note from PDF upload for user {}", userId);

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new IllegalArgumentException("Only PDF files are allowed. Received: " + contentType);
        }

        String extractedText = extractTextFromPdf(file);
        if (extractedText.isBlank()) {
            throw new IllegalArgumentException(
                    "Could not extract any text from the PDF. The file might be image-based (scanned)."
            );
        }
        logger.info("Extracted {} characters from PDF", extractedText.length());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String textForAi = truncateForAi(extractedText);
        AiSummaryResult aiResult = aiSummaryService.summarize(textForAi);

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

    // uses Spring AI's PDF reader (Apache PDFBox under the hood)
    private String extractTextFromPdf(MultipartFile file) {
        try {
            Resource pdfResource = file.getResource();
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource);
            List<Document> documents = pdfReader.read();

            return documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n"));

        } catch (Exception e) {
            logger.error("Failed to extract text from PDF: {}", e.getMessage());
            throw new RuntimeException("Failed to read PDF file: " + e.getMessage(), e);
        }
    }

    private String truncateForAi(String text) {
        if (text.length() <= MAX_CHARS_FOR_AI) {
            return text;
        }
        logger.warn("Text too long ({} chars), truncating to {} chars for AI",
                text.length(), MAX_CHARS_FOR_AI);
        return text.substring(0, MAX_CHARS_FOR_AI)
                + "\n\n[Note: The text was truncated due to length. Please summarize based on the above content.]";  
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> getUserNotes(Long userId) {
        logger.info("Fetching notes for user {}", userId);
        List<Note> notes = noteRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return notes.stream()
                .map(NoteResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // uses findByIdAndUserId to make sure users can only see their own notes
    @Transactional(readOnly = true)
    public NoteResponse getNoteById(Long noteId, Long userId) {
        Note note = noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Note not found with id: " + noteId
                ));
        return NoteResponse.fromEntity(note);
    }

    @Transactional
    public void deleteNote(Long noteId, Long userId) {
        logger.info("Deleting note {} for user {}", noteId, userId);

        Note note = noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Note not found with id: " + noteId
                ));

        noteRepository.delete(note);
        logger.info("Note {} deleted successfully", noteId);
    }
}
