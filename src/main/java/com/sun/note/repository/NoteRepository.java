package com.sun.note.repository;

import com.sun.note.domain.entity.Note;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteRepository extends JpaRepository<Note, Long> {
    List<Note> findByUserIdAndCategoryIdAndDeleted(UUID userId, Long categoryId, boolean deleted);
}
