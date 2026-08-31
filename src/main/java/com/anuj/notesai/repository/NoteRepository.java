package com.anuj.notesai.repository;

import com.anuj.notesai.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {

    // get all notes for a user, newest first
    List<Note> findByUserIdOrderByCreatedAtDesc(Long userId);

    // find a note only if it belongs to the given user (prevents accessing other users' notes)
    Optional<Note> findByIdAndUserId(Long id, Long userId);
}
