package com.sun.note.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record NoteResponse(
        Long id,
        UUID userId,
        Long categoryId,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
    public static NoteResponse of(
            Long id,
            UUID userId,
            Long categoryId,
            String title,
            String content,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        return new NoteResponse(
                id,
                userId,
                categoryId,
                title,
                content,
                createdAt,
                updatedAt);
    }

       public static NoteResponse of(
            Long id,
            UUID userId,
            Long categoryId,
            String title,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        return new NoteResponse(
                id,
                userId,
                categoryId,
                title,
                null,
                createdAt,
                updatedAt);
    }
}