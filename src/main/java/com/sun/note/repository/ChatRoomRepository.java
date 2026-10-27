package com.sun.note.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.sun.note.domain.entity.ChatRoom;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    List<ChatRoom> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId);

    // 전송 잠금 획득. 본인 채팅방이고 잠겨 있지 않거나(또는 만료) 이면 1, 아니면 0.
    // 조건 검사와 갱신이 한 UPDATE 문이라 동시 요청 중 하나만 성공한다.
    @Modifying
    @Query("""
            update ChatRoom r set r.pendingUntil = :until
            where r.id = :id and r.userId = :userId
              and (r.pendingUntil is null or r.pendingUntil < :now)
            """)
    int acquireSendLock(@Param("id") Long id, @Param("userId") UUID userId,
            @Param("now") LocalDateTime now, @Param("until") LocalDateTime until);

    // 아래 두 쿼리는 획득 시 설정한 만료 시각(until)이 그대로일 때만, 즉 내 잠금일 때만 갱신한다.
    // (내 잠금이 만료된 뒤 다른 요청이 새로 잡은 잠금을 풀지 않기 위해) 0이면 이미 잠금을 잃은 것이다.

    // 전송 성공: 잠금 해제 + 모델 갱신
    @Modifying
    @Query("""
            update ChatRoom r set r.pendingUntil = null, r.modelId = :modelId, r.updatedAt = :now
            where r.id = :id and r.pendingUntil = :until
            """)
    int completeSend(@Param("id") Long id, @Param("until") LocalDateTime until,
            @Param("modelId") String modelId, @Param("now") LocalDateTime now);

    // 전송 실패: 잠금만 해제 (모델은 그대로)
    @Modifying
    @Query("update ChatRoom r set r.pendingUntil = null where r.id = :id and r.pendingUntil = :until")
    int releaseSendLock(@Param("id") Long id, @Param("until") LocalDateTime until);
}
