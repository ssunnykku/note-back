package com.sun.note.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sun.note.domain.entity.ChatMessage;
import com.sun.note.domain.entity.ChatRole;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByChatRoomIdOrderByCreatedAtAscIdAsc(Long chatRoomId);

    // 최근 메시지부터 (AI에 보낼 이전 대화 범위를 정할 때 사용)
    List<ChatMessage> findByChatRoomIdOrderByCreatedAtDescIdDesc(Long chatRoomId, Pageable pageable);

    // 사용자의 모든 채팅방에서 since 이후 저장된 특정 role 메시지 수 (전송 횟수 제한)
    // 실패한 전송은 보상 처리로 삭제되므로 세지 않는다.
    @Query("""
            select count(m) from ChatMessage m
            where m.role = :role and m.createdAt > :since
              and m.chatRoomId in (select r.id from ChatRoom r where r.userId = :userId)
            """)
    long countByUserIdAndRoleSince(@Param("userId") UUID userId, @Param("role") ChatRole role,
            @Param("since") LocalDateTime since);

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
