package com.sun.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditNoteRequest(
        Long categoryId,
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 100000) String content) {

    public static EditNoteRequest of(Long categoryId, String title, String content) {
        return new EditNoteRequest(categoryId, title, content);
    }
}
