package com.sun.note.dto;

import java.util.UUID;

public record CreateNoteRequest(UUID userId, Long categoryId, String title, String content) {

    public static CreateNoteRequest of(UUID userId, Long categoryId, String title, String content) {
        return new CreateNoteRequest(userId, categoryId, title, content);
    }
}