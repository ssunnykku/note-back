package com.sun.note.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class JwtUtilTest {

    private JwtUtil jwtUtil;

    private static final String SECRET = "egR9Ba9Uo9uFOxfOKPWV0V8ICiVEZQGjoLr37FKNcXA=";
    private static final String USERID = "d4840b70-2cbb-4e20-8c9a-0fdb6da42fae";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 1800000); // 30분(테스트용)
    }

    @Nested
    @DisplayName("토큰 생성")
    class GenerateToken {

        @Test
        @DisplayName("accessToken 생성 성공 - username으로 토큰 생성")
        void generateAccessToken_success() {
            // given & when
            String token = jwtUtil.generateToken(USERID);

            // then
            assertThat(token).isNotNull();
            assertThat(token).isNotEmpty();
            assertThat(token.split("\\.")).hasSize(3); // 토큰 형태인지 확인
        }
    }

    @Nested
    @DisplayName("토큰 검증")
    class ValidateToken {

        @Test
        @DisplayName("토큰 검증 성공 - 유효한 토큰에서 username 추출")
        void validateToken_success() {
            // given
            String token = jwtUtil.generateToken(USERID);

            // when
            String username =  jwtUtil.validateToken(token);

            // then
            assertThat(USERID).isEqualTo(username);
        }

        @Test
        @DisplayName("토큰 검증 실패 - 만료된 토큰")
        void validateToken_fail_expired() {
            // given
            JwtUtil util = new JwtUtil(SECRET, 0);
            String expiredToken = util.generateToken(USERID);

            // when
            String validate = jwtUtil.validateToken(expiredToken);

            // then
            assertThat(validate).isEqualTo(null);
        }

        @Test
        @DisplayName("토큰 검증 실패 - 위변조된 토큰 (서명 불일치)")
        void validateToken_fail_invalidSignature() {
            // given
            jwtUtil.generateToken(USERID);

            JwtUtil util = new JwtUtil("Mfj6zEMpm3cbMQDPr/eYZlXNm07MERgQotg1qm824kw=", 1800000);
            String tamperedToken = util.generateToken(USERID);

            // when
            String validate = jwtUtil.validateToken(tamperedToken);

            // then
            assertThat(validate).isEqualTo(null);
        }

        @Test
        @DisplayName("토큰 검증 실패 - 잘못된 형식의 토큰")
        void validateToken_fail_malformedToken() {
            // given
            String tamperedToken = "not.a.jwt";

            // when
            String validate = jwtUtil.validateToken(tamperedToken);

            // then
            assertThat(validate).isEqualTo(null);
        }

        @Test
        @DisplayName("토큰 검증 실패 - null 토큰")
        void validateToken_fail_nullToken() {
            // given & when
            String validate = jwtUtil.validateToken(null);

            // then
            assertThat(validate).isNull();
        }
    }
}
