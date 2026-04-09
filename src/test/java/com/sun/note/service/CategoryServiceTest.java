package com.sun.note.service;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sun.note.domain.entity.Category;
import com.sun.note.dto.CategoryResponse;
import com.sun.note.exception.BusinessException;
import com.sun.note.exception.ErrorCode;
import com.sun.note.repository.CategoryRepository;

@ExtendWith(MockitoExtension.class)
public class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private static final String NAME = "일상";
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    @DisplayName("카테고리 생성")
    void testAddCategory() {
        // given
        Category category = Category.of(NAME, USER_ID);
        when(categoryRepository.save(any(Category.class))).thenReturn(category);

        // when
        CategoryResponse result = categoryService.addCategory(NAME, USER_ID);

        // then
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo(NAME);
    }

    @Test
    @DisplayName("카테고리 수정")
    void testEditCategory() {
        // given
        Category category = Category.of(NAME, USER_ID);
        when(categoryRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(category));

        // when
        CategoryResponse result = categoryService.editCategory(1L, "개발", USER_ID);

        // then
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("개발");
    }

    @Test
    @DisplayName("존재하지 않는 카테고리 수정 시 예외 처리")
    void testEditCategoryException() {
        when(categoryRepository.findByIdAndUserId(999L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.editCategory(999L, "개발", USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.RESOURCE_NOT_FOUND.getMessage());
    }
}
