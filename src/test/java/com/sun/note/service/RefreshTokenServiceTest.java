package com.sun.note.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sun.note.config.AuthProperties;
import com.sun.note.domain.entity.RefreshToken;
import com.sun.note.exception.BusinessException;
import com.sun.note.exception.ErrorCode;
import com.sun.note.repository.RefreshTokenRepository;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Spy
    private AuthProperties authProperties = new AuthProperties(
            new AuthProperties.RefreshToken(Duration.ofDays(7), false));

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private static final UUID USER_ID = UUID.fromString("2013a306-9369-46ba-ac55-2f547ac5c50f");

    private RefreshToken savedToken() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("발급")
    class Issue {

        @Test
        @DisplayName("원본 토큰은 저장하지 않고 SHA-256 해시만 저장한다")
        void issue_storesHashOnly() {
            String raw = refreshTokenService.issue(USER_ID);

            RefreshToken saved = savedToken();
            assertThat(saved.getTokenHash()).isNotEqualTo(raw).isEqualTo(RefreshTokenService.hash(raw));
            assertThat(saved.getTokenHash()).hasSize(64);
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("만료 시각은 발급 시각 + 설정된 유효기간(7일)")
        void issue_expiresAfterTtl() {
            LocalDateTime before = LocalDateTime.now();

            refreshTokenService.issue(USER_ID);

            LocalDateTime after = LocalDateTime.now();
            assertThat(savedToken().getExpiresAt())
                    .isBetween(before.plusDays(7), after.plusDays(7));
        }

        @Test
        @DisplayName("발급할 때마다 서로 다른 추측 불가능한 토큰")
        void issue_randomTokens() {
            String first = refreshTokenService.issue(USER_ID);
            String second = refreshTokenService.issue(USER_ID);

            assertThat(first).isNotEqualTo(second);
            assertThat(first).matches("[A-Za-z0-9_-]{43}"); // 32바이트 base64url
        }
    }

    @Nested
    @DisplayName("회전")
    class Rotate {

        @Test
        @DisplayName("유효한 토큰이면 기존 토큰을 지우고 같은 사용자에게 새 토큰을 발급한다")
        void rotate_success() {
            String oldRaw = "old-token";
            String oldHash = RefreshTokenService.hash(oldRaw);
            when(refreshTokenRepository.findByTokenHash(oldHash))
                    .thenReturn(Optional.of(RefreshToken.of(USER_ID, oldHash, LocalDateTime.now().plusDays(1))));
            when(refreshTokenRepository.deleteValid(eq(oldHash), any())).thenReturn(1);

            RefreshTokenService.Rotated rotated = refreshTokenService.rotate(oldRaw);

            assertThat(rotated.userId()).isEqualTo(USER_ID);
            assertThat(rotated.refreshToken()).isNotEqualTo(oldRaw);
            assertThat(savedToken().getTokenHash()).isEqualTo(RefreshTokenService.hash(rotated.refreshToken()));
        }

        @Test
        @DisplayName("저장되지 않은 토큰이면 INVALID_REFRESH_TOKEN")
        void rotate_unknown() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService.rotate("unknown"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.INVALID_REFRESH_TOKEN.getMessage());
        }

        @Test
        @DisplayName("만료됐거나 다른 요청이 먼저 회전해 지울 수 없으면 INVALID_REFRESH_TOKEN")
        void rotate_notDeleted() {
            String hash = RefreshTokenService.hash("used");
            when(refreshTokenRepository.findByTokenHash(hash))
                    .thenReturn(Optional.of(RefreshToken.of(USER_ID, hash, LocalDateTime.now().minusDays(1))));
            when(refreshTokenRepository.deleteValid(eq(hash), any())).thenReturn(0);

            assertThatThrownBy(() -> refreshTokenService.rotate("used"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.INVALID_REFRESH_TOKEN.getMessage());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = { " " })
        @DisplayName("쿠키가 없으면 INVALID_REFRESH_TOKEN")
        void rotate_missing(String raw) {
            assertThatThrownBy(() -> refreshTokenService.rotate(raw))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.INVALID_REFRESH_TOKEN.getMessage());
            verifyNoInteractions(refreshTokenRepository);
        }
    }

    @Nested
    @DisplayName("폐기")
    class Revoke {

        @Test
        @DisplayName("해시로 찾아 삭제한다")
        void revoke_deletesByHash() {
            refreshTokenService.revoke("token");

            verify(refreshTokenRepository).deleteByTokenHash(RefreshTokenService.hash("token"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("쿠키가 없으면 아무것도 하지 않는다")
        void revoke_missing(String raw) {
            refreshTokenService.revoke(raw);

            verifyNoInteractions(refreshTokenRepository);
        }
    }
}
