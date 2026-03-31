package com.sun.note.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateNoteRequest(
        Long categoryId,
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 100000) String content) {

    public static CreateNoteRequest of(String title, String content) {
        return new CreateNoteRequest(null, title, content);
    }

    public static CreateNoteRequest of(Long categoryId, String title, String content) {
        return new CreateNoteRequest(categoryId, title, content);
    }
}
