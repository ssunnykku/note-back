package com.sun.note.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sun.note.domain.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByChatRoomIdOrderByCreatedAtAsc(Long chatRoomId);
}
