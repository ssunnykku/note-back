package com.sun.note.dto;

import java.time.LocalDateTime;

import com.sun.note.domain.entity.ChatMessage;
import com.sun.note.domain.entity.ChatRole;

public record ChatMessageResponse(
        Long id,
        ChatRole role,
        String content,
        LocalDateTime createdAt) {

    public static ChatMessageResponse of(Long id, ChatRole role, String content, LocalDateTime createdAt) {
        return new ChatMessageResponse(id, role, content, createdAt);
    }

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(message.getId(), message.getRole(), message.getContent(),
                message.getCreatedAt());
    }
}
