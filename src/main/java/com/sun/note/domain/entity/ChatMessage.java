package com.sun.note.domain.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_messages")
@Getter
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Long id;

    @NotNull
    @Column(name = "chat_room_id")
    private Long chatRoomId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatRole role;

    @NotNull
    @Column(columnDefinition = "TEXT")
    private String content;

    @CreatedDate
    @Column(updatable = false, nullable = false, name = "created_at")
    private LocalDateTime createdAt;

    public static ChatMessage of(Long chatRoomId, ChatRole role, String content) {
        return ChatMessage.builder()
                .chatRoomId(chatRoomId)
                .role(role)
                .content(content)
                .build();
    }

    public static ChatMessage of(Long id, Long chatRoomId, ChatRole role, String content, LocalDateTime createdAt) {
        return ChatMessage.builder()
                .id(id)
                .chatRoomId(chatRoomId)
                .role(role)
                .content(content)
                .createdAt(createdAt)
                .build();
    }
}
