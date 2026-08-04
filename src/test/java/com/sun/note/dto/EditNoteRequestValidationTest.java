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

class EditNoteRequestValidationTest {

    private Validator validator;

    private static final Long VALID_CATEGORY_ID = 1L;
    private static final String VALID_TITLE = "제목";
    private static final String VALID_CONTENT = "내용";

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("유효한 요청이면 검증 통과")
    void validRequest() {
        var request = EditNoteRequest.of(VALID_CATEGORY_ID, VALID_TITLE, VALID_CONTENT, 1L);
        Set<ConstraintViolation<EditNoteRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Nested
    @DisplayName("categoryId 검증")
    class CategoryIdValidation {

        @Test
        @DisplayName("null이어도 통과 (카테고리 변경 선택적)")
        void nullCategoryId() {
            var request = EditNoteRequest.of(null, VALID_TITLE, VALID_CONTENT, 1L);
            Set<ConstraintViolation<EditNoteRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("title 검증")
    class TitleValidation {

        @Test
        @DisplayName("null이면 실패")
        void nullTitle() {
            var request = EditNoteRequest.of(VALID_CATEGORY_ID, null, VALID_CONTENT, 1L);
            Set<ConstraintViolation<EditNoteRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("빈 문자열이면 실패")
        void blankTitle() {
            var request = EditNoteRequest.of(VALID_CATEGORY_ID, "  ", VALID_CONTENT, 1L);
            Set<ConstraintViolation<EditNoteRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("255자 경계값 - 통과")
        void titleAtMaxLength() {
            String title = "a".repeat(255);
            var request = EditNoteRequest.of(VALID_CATEGORY_ID, title, VALID_CONTENT, 1L);
            Set<ConstraintViolation<EditNoteRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("256자 경계값 - 실패")
        void titleExceedsMaxLength() {
            String title = "a".repeat(256);
            var request = EditNoteRequest.of(VALID_CATEGORY_ID, title, VALID_CONTENT, 1L);
            Set<ConstraintViolation<EditNoteRequest>> violations = validator.validate(request);
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("title");
        }
    }

    @Nested
    @DisplayName("version 검증")
    class VersionValidation {

        @Test
        @DisplayName("null이면 실패")
        void nullVersion() {
            var request = EditNoteRequest.of(VALID_CATEGORY_ID, VALID_TITLE, VALID_CONTENT, null);
            Set<ConstraintViolation<EditNoteRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("version");
        }
    }

    @Nested
    @DisplayName("content 검증")
    class ContentValidation {

        @Test
        @DisplayName("null이면 실패")
        void nullContent() {
            var request = EditNoteRequest.of(VALID_CATEGORY_ID, VALID_TITLE, null, 1L);
            Set<ConstraintViolation<EditNoteRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("빈 문자열이면 실패")
        void blankContent() {
            var request = EditNoteRequest.of(VALID_CATEGORY_ID, VALID_TITLE, "  ", 1L);
            Set<ConstraintViolation<EditNoteRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("100000자 경계값 - 통과")
        void contentAtMaxLength() {
            String content = "a".repeat(100000);
            var request = EditNoteRequest.of(VALID_CATEGORY_ID, VALID_TITLE, content, 1L);
            Set<ConstraintViolation<EditNoteRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("100001자 경계값 - 실패")
        void contentExceedsMaxLength() {
            String content = "a".repeat(100001);
            var request = EditNoteRequest.of(VALID_CATEGORY_ID, VALID_TITLE, content, 1L);
            Set<ConstraintViolation<EditNoteRequest>> violations = validator.validate(request);
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("content");
        }
    }
}
