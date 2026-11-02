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

    // AI 채팅 전송을 사용자 단위로 직렬화한다. 동시 요청에서도 전송 잠금과 횟수 제한이 정확하도록
    @Query(value = "select id from users where id = :id for update", nativeQuery = true)
    Optional<UUID> lockForChat(@Param("id") UUID id);
}
