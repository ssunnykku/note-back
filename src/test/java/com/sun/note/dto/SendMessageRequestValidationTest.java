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

class SendMessageRequestValidationTest {

    private Validator validator;

    private static final String VALID_CONTENT = "안녕하세요";
    private static final String VALID_MODEL_ID = "qwen3:8b";

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("유효한 요청이면 검증 통과")
    void validRequest() {
        var request = SendMessageRequest.of(VALID_CONTENT, VALID_MODEL_ID);
        Set<ConstraintViolation<SendMessageRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Nested
    @DisplayName("content 검증")
    class ContentValidation {

        @Test
        @DisplayName("null이면 실패")
        void nullContent() {
            var request = SendMessageRequest.of(null, VALID_MODEL_ID);
            Set<ConstraintViolation<SendMessageRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("빈 문자열이면 실패")
        void blankContent() {
            var request = SendMessageRequest.of("  ", VALID_MODEL_ID);
            Set<ConstraintViolation<SendMessageRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("10000자 경계값 - 통과")
        void contentAtMaxLength() {
            String content = "a".repeat(10000);
            var request = SendMessageRequest.of(content, VALID_MODEL_ID);
            Set<ConstraintViolation<SendMessageRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("10001자 경계값 - 실패")
        void contentExceedsMaxLength() {
            String content = "a".repeat(10001);
            var request = SendMessageRequest.of(content, VALID_MODEL_ID);
            Set<ConstraintViolation<SendMessageRequest>> violations = validator.validate(request);
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("content");
        }
    }

    @Nested
    @DisplayName("modelId 검증")
    class ModelIdValidation {

        @Test
        @DisplayName("null이면 실패")
        void nullModelId() {
            var request = SendMessageRequest.of(VALID_CONTENT, null);
            Set<ConstraintViolation<SendMessageRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("빈 문자열이면 실패")
        void blankModelId() {
            var request = SendMessageRequest.of(VALID_CONTENT, "  ");
            Set<ConstraintViolation<SendMessageRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
        }
    }
}
