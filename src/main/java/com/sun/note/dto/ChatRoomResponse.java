package com.sun.note.dto;

import java.time.LocalDateTime;

public record ChatRoomResponse(
        Long id,
        String title,
        String modelId,
        String lastMessage,
        LocalDateTime createdAt) {

    public static ChatRoomResponse of(Long id, String title, String modelId, String lastMessage,
            LocalDateTime createdAt) {
        return new ChatRoomResponse(id, title, modelId, lastMessage, createdAt);
    }
}
