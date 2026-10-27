package com.sun.note.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sun.note.domain.entity.ChatMessage;
import com.sun.note.domain.entity.ChatRole;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByChatRoomIdOrderByCreatedAtAscIdAsc(Long chatRoomId);

    // 채팅방별 마지막 메시지 (id는 IDENTITY라 가장 큰 id가 가장 최근 메시지)
    @Query("""
            select m from ChatMessage m
            where m.id in (
                select max(m2.id) from ChatMessage m2
                where m2.chatRoomId in :chatRoomIds
                group by m2.chatRoomId)
            """)
    List<ChatMessage> findLastMessagesByChatRoomIds(@Param("chatRoomIds") Collection<Long> chatRoomIds);

    // 채팅방별 특정 role의 첫 메시지
    @Query("""
            select m from ChatMessage m
            where m.id in (
                select min(m2.id) from ChatMessage m2
                where m2.chatRoomId in :chatRoomIds and m2.role = :role
                group by m2.chatRoomId)
            """)
    List<ChatMessage> findFirstMessagesByChatRoomIds(
            @Param("chatRoomIds") Collection<Long> chatRoomIds,
            @Param("role") ChatRole role);
}
