package com.sun.note.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// id는 프론트에서 채팅방 생성 시점에 발급한 값(Date.now())을 그대로 사용한다.
// 별도의 "채팅방 생성" API 없이, 첫 메시지 전송 시 해당 id로 upsert 된다.
@Entity
@Table(name = "chat_rooms")
@Getter
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ChatRoom {

    @Id
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

    public static ChatRoom of(Long id, UUID userId, String modelId) {
        return ChatRoom.builder()
                .id(id)
                .userId(userId)
                .modelId(modelId)
                .build();
    }

    public void updateModel(String modelId) {
        this.modelId = modelId;
    }
}
