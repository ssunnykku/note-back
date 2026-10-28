package com.sun.note.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sun.note.domain.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // 만료되지 않은 토큰만 삭제한다. 같은 토큰으로 동시에 회전하면 행 잠금 때문에 한 요청만 1을 받는다.
    @Modifying
    @Query("delete from RefreshToken t where t.tokenHash = :hash and t.expiresAt > :now")
    int deleteValid(@Param("hash") String hash, @Param("now") LocalDateTime now);

    @Modifying
    @Query("delete from RefreshToken t where t.tokenHash = :hash")
    int deleteByTokenHash(@Param("hash") String hash);

    // 발급 시 해당 사용자의 만료된 토큰을 정리한다. (로그아웃 없이 떠난 기기의 토큰이 쌓이지 않도록)
    @Modifying
    @Query("delete from RefreshToken t where t.userId = :userId and t.expiresAt <= :now")
    int deleteExpiredByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
}
