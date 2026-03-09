package com.sun.note.dto;

public record EditNoteRequest(Long categoryId, String title, String content) {

    public static EditNoteRequest of(Long categoryId, String title, String content) {
        return new EditNoteRequest(categoryId, title, content);
    }
}
