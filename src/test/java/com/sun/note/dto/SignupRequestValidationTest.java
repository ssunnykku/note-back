package com.sun.note.dto;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import static org.assertj.core.api.Assertions.assertThat;

class SignupRequestValidationTest {

    private Validator validator;

    private static final String VALID_NAME = "홍길";
    private static final String VALID_EMAIL = "user@test.com";
    private static final String VALID_PASSWORD = "password123";

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private Set<ConstraintViolation<SignupRequest>> validate(String name, String email) {
        return validator.validate(new SignupRequest(name, email, VALID_PASSWORD, VALID_PASSWORD));
    }

    @Test
    @DisplayName("유효한 요청이면 검증 통과")
    void validRequest() {
        assertThat(validate(VALID_NAME, VALID_EMAIL)).isEmpty();
    }

    @Nested
    @DisplayName("name 검증")
    class NameValidation {

        @Test
        @DisplayName("null이면 실패")
        void nullName() {
            assertThat(validate(null, VALID_EMAIL)).isNotEmpty();
        }

        @Test
        @DisplayName("빈 문자열이면 실패")
        void blankName() {
            assertThat(validate("  ", VALID_EMAIL)).isNotEmpty();
        }

        @Test
        @DisplayName("1자면 실패")
        void tooShort() {
            assertThat(validate("a", VALID_EMAIL)).isNotEmpty();
        }

        @Test
        @DisplayName("50자 경계값 - 통과")
        void atMaxLength() {
            assertThat(validate("a".repeat(50), VALID_EMAIL)).isEmpty();
        }

        @Test
        @DisplayName("51자면 실패")
        void overMaxLength() {
            assertThat(validate("a".repeat(51), VALID_EMAIL)).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("email 검증")
    class EmailValidation {

        @Test
        @DisplayName("null이면 실패")
        void nullEmail() {
            assertThat(validate(VALID_NAME, null)).isNotEmpty();
        }

        @Test
        @DisplayName("이메일 형식이 아니면 실패")
        void invalidFormat() {
            assertThat(validate(VALID_NAME, "not-an-email")).isNotEmpty();
        }

        @Test
        @DisplayName("255자 초과면 실패")
        void overMaxLength() {
            String email = "a".repeat(250) + "@t.com";
            assertThat(validate(VALID_NAME, email)).isNotEmpty();
        }
    }
}
