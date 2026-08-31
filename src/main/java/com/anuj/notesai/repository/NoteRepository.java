package com.anuj.notesai.repository;

import com.anuj.notesai.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access layer for notes. All queries are user-scoped to prevent
 * unauthorized access to other users' notes (application-level row security).
 */
@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Note> findByIdAndUserId(Long id, Long userId);
}
