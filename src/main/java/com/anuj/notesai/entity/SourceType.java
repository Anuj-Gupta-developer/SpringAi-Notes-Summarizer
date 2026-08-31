package com.anuj.notesai.entity;

/**
 * Tracks how a note was created — either from pasted text or a PDF upload.
 * Stored as a string in MySQL via @Enumerated(EnumType.STRING).
 */
public enum SourceType {
    PASTED_TEXT,
    PDF_UPLOAD
}
