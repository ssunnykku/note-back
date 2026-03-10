package com.sun.note.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

public record CategoryNoteDto(Long id, String categoryName, List<NoteDto> notes) {
    public static CategoryNoteDto of(Long id, String categoryName, List<NoteDto> notes) {
        return new CategoryNoteDto(id, categoryName, notes);
    }

    public record NoteDto(Long id, UUID userId, String title, String content, LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        public static NoteDto of(Long id, UUID userId, String title, String content,
                LocalDateTime createdAt,
                LocalDateTime updatedAt) {
            return new NoteDto(id, userId, title, content, createdAt, updatedAt);
        }

    }
}
