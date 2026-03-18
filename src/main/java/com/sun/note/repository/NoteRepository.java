package com.sun.note.repository;

import com.sun.note.domain.entity.Note;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteRepository extends JpaRepository<Note, Long> {

}
