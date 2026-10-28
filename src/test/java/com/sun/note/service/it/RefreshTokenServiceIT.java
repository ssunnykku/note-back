package com.sun.note.service.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.sun.note.exception.BusinessException;
import com.sun.note.exception.ErrorCode;
import com.sun.note.service.UserService;
import com.sun.note.service.UserService.AuthResult;

// 실제 DB에서 refresh 토큰 발급/회전/만료/폐기와 동시 회전을 검증한다.
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class RefreshTokenServiceIT {

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String EMAIL = "refresh-it@test.com";
    private static final String PASSWORD = "password1234";

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from users where email = ?", EMAIL);
    }

    private AuthResult signUp() {
        return userService.signUp("사용자", EMAIL, PASSWORD, PASSWORD);
    }

    private int tokenCount() {
        return jdbcTemplate.queryForObject("""
                select count(*) from refresh_tokens t join users u on u.id = t.user_id where u.email = ?
                """, Integer.class, EMAIL);
    }

    @Test
    @DisplayName("회원가입하면 refresh 토큰이 발급되고 DB에는 원본이 아닌 해시가 저장된다")
    void signUp_issuesRefreshToken() {
        AuthResult result = signUp();

        assertThat(result.refreshToken()).isNotBlank();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where token_hash = ?", Integer.class, result.refreshToken()))
                .isZero();
        assertThat(tokenCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("갱신하면 새 토큰으로 바뀌고 이전 토큰은 다시 쓸 수 없다")
    void refresh_rotates() {
        String first = signUp().refreshToken();

        AuthResult refreshed = userService.refresh(first);

        assertThat(refreshed.refreshToken()).isNotEqualTo(first);
        assertThat(refreshed.response().user().email()).isEqualTo(EMAIL);
        assertThat(tokenCount()).isEqualTo(1);
        assertThatThrownBy(() -> userService.refresh(first))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        assertThat(userService.refresh(refreshed.refreshToken()).refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("만료된 토큰은 거절한다")
    void refresh_expired() {
        String token = signUp().refreshToken();
        jdbcTemplate.update("""
                update refresh_tokens set expires_at = now() - interval '1 second'
                where user_id = (select id from users where email = ?)
                """, EMAIL);

        assertThatThrownBy(() -> userService.refresh(token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("로그아웃한 토큰은 거절한다")
    void logout_revokes() {
        String token = signUp().refreshToken();

        userService.logout(token);

        assertThat(tokenCount()).isZero();
        assertThatThrownBy(() -> userService.refresh(token))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("기기(로그인)마다 토큰이 따로 있고, 한 기기의 로그아웃은 다른 기기에 영향이 없다")
    void multipleDevices() {
        String deviceA = signUp().refreshToken();
        String deviceB = userService.login(EMAIL, PASSWORD).refreshToken();

        userService.logout(deviceA);

        assertThat(userService.refresh(deviceB).refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("발급할 때 해당 사용자의 만료된 토큰을 정리한다")
    void issue_cleansExpired() {
        signUp();
        jdbcTemplate.update("""
                update refresh_tokens set expires_at = now() - interval '1 second'
                where user_id = (select id from users where email = ?)
                """, EMAIL);

        userService.login(EMAIL, PASSWORD);

        assertThat(tokenCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 토큰으로 동시에 갱신하면 한 요청만 성공한다")
    void refresh_concurrent() throws Exception {
        String token = signUp().refreshToken();
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        userService.refresh(token);
                        return true;
                    } catch (BusinessException e) {
                        return false;
                    }
                }));
            }
            start.countDown();

            int successes = 0;
            for (Future<Boolean> f : futures) {
                if (f.get()) {
                    successes++;
                }
            }
            assertThat(successes).isEqualTo(1);
            assertThat(tokenCount()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("사용자를 삭제하면 토큰도 함께 삭제된다")
    void userDelete_cascades() {
        UUID userId = jdbcTemplate.queryForObject("select id from users where email = ?", UUID.class,
                signUp().response().user().email());

        jdbcTemplate.update("delete from users where id = ?", userId);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where user_id = ?", Integer.class, userId)).isZero();
    }
}
