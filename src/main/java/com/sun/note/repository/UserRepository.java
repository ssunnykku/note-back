package com.sun.note.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sun.note.domain.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    // AI 채팅 전송 시 사용자 단위로 직렬화하기 위한 행 잠금 (트랜잭션 끝까지 유지)
    // 사용자당 동시 전송 제한과 전송 횟수 제한을 동시 요청에서도 정확히 지키기 위해 사용한다.
    @Query(value = "select id from users where id = :id for update", nativeQuery = true)
    Optional<UUID> lockForChat(@Param("id") UUID id);
}
