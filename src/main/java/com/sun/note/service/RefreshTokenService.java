package com.sun.note.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sun.note.config.AuthProperties;
import com.sun.note.domain.entity.RefreshToken;
import com.sun.note.exception.BusinessException;
import com.sun.note.exception.ErrorCode;
import com.sun.note.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;

// refresh 토큰 발급/회전/폐기. 원본 토큰은 반환만 하고 DB에는 해시만 남긴다(DB가 유출돼도 토큰으로 쓸 수 없음).
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthProperties authProperties;

    public record Rotated(UUID userId, String refreshToken) {
    }

    @Transactional
    public String issue(UUID userId) {
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.deleteExpiredByUserId(userId, now);

        String raw = newToken();
        refreshTokenRepository.save(RefreshToken.of(userId, hash(raw), now.plus(authProperties.refreshToken().ttl())));
        return raw;
    }

    // 사용한 토큰은 지우고 새 토큰을 발급한다. 한 번 쓴 토큰은 다시 쓸 수 없다.
    // 한계: 탈취한 쪽이 먼저 갱신하면 그쪽 토큰이 유효하게 이어지고(정상 사용자만 401), 재사용을 탐지하거나
    // 절대 만료(최초 로그인 기준 상한)를 두지는 않는다. 7일 안에 계속 갱신하면 세션이 무기한 유지된다.
    @Transactional
    public Rotated rotate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        String hash = hash(raw);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        // 만료됐거나, 같은 토큰으로 동시에 들어온 다른 요청이 먼저 지웠으면 0
        if (refreshTokenRepository.deleteValid(hash, LocalDateTime.now()) == 0) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        return new Rotated(token.getUserId(), issue(token.getUserId()));
    }

    @Transactional
    public void revoke(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        refreshTokenRepository.deleteByTokenHash(hash(raw));
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
