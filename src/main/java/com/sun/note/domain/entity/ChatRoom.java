package com.sun.note.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
@Table(name = "chat_rooms")
@Getter
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Long id;

    @NotNull
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "model_id")
    private String modelId;

    @CreatedDate
    @Column(updatable = false, nullable = false, name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false, name = "updated_at")
    private LocalDateTime updatedAt;

    // 메시지 전송(AI 응답 대기) 중인 동안의 잠금 만료 시각. null이거나 지났으면 전송 가능.
    // 서버가 응답 도중 죽어도 만료 후 자동으로 풀린다. 갱신은 ChatRoomRepository의 bulk update로만 한다.
    @Column(name = "pending_until")
    private LocalDateTime pendingUntil;

    public static ChatRoom of(UUID userId, String modelId) {
        return ChatRoom.builder()
                .userId(userId)
                .modelId(modelId)
                .build();
    }

    public static ChatRoom of(Long id, UUID userId, String modelId) {
        return ChatRoom.builder()
                .id(id)
                .userId(userId)
                .modelId(modelId)
                .build();
    }
}
