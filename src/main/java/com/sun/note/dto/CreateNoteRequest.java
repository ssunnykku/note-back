package com.sun.note.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateNoteRequest(
        @NotNull UUID userId,
        Long categoryId,
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 100000) String content
) {
    
    public static CreateNoteRequest of(UUID userId, String title, String content) {
        return new CreateNoteRequest(userId, null, title, content);
    }

    public static CreateNoteRequest of(UUID userId, Long categoryId, String title, String content) {
        return new CreateNoteRequest(userId, categoryId, title, content);
    }
}
