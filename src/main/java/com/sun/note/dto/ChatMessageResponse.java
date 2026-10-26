package com.sun.note.dto;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        String content,
        LocalDateTime createdAt) {

    public static ChatMessageResponse of(Long id, String content, LocalDateTime createdAt) {
        return new ChatMessageResponse(id, content, createdAt);
    }
}
