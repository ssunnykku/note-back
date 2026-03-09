package com.sun.note.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity
@Table(name = "notes")
@Getter
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    private UUID userId;

    @NotNull
    private Long categoryId;

    @NotNull
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static Note of() {
        return Note.builder().build();
    };

    public static Note of(Long id, UUID userId, Long categoryId, String title, String content) {
        return Note.builder()
                .id(id)
                .userId(userId)
                .categoryId(categoryId)
                .title(title)
                .content(content)
                .build();
    }

    public static Note of(UUID userId, Long categoryId, String title, String content) {
        return Note.builder()
                .userId(userId)
                .categoryId(categoryId)
                .title(title)
                .content(content)
                .build();
    }

    public void edit(Long categoryId, String title, String content) {
        this.categoryId = categoryId;
        this.title = title;
        this.content = content;
    }

}