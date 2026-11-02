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

    // 다른 방 동시 요청은 이 쿼리만으로는 둘 다 성공할 수 있어, 호출 전에 UserRepository.lockForChat이 필요하다
    @Modifying
    @Query("""
            update ChatRoom r set r.pendingUntil = :until
            where r.id = :id and r.userId = :userId
              and (r.pendingUntil is null or r.pendingUntil < :now)
              and not exists (
                  select 1 from ChatRoom o
                  where o.userId = :userId and o.id <> :id and o.pendingUntil >= :now)
            """)
    int acquireSendLock(@Param("id") Long id, @Param("userId") UUID userId,
            @Param("now") LocalDateTime now, @Param("until") LocalDateTime until);

    // 아래 두 쿼리는 until로 내 잠금인지 확인한다. 만료 후 다른 요청이 잡은 잠금을 풀지 않기 위해 (0이면 잠금을 잃음)
    @Modifying
    @Query("""
            update ChatRoom r set r.pendingUntil = null, r.modelId = :modelId, r.updatedAt = :now
            where r.id = :id and r.pendingUntil = :until
            """)
    int completeSend(@Param("id") Long id, @Param("until") LocalDateTime until,
            @Param("modelId") String modelId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("update ChatRoom r set r.pendingUntil = null where r.id = :id and r.pendingUntil = :until")
    int releaseSendLock(@Param("id") Long id, @Param("until") LocalDateTime until);
}
