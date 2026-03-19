package com.sun.note.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.sun.note.repository.CategoryRepository;

@ExtendWith(MockitoExtension.class)
public class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private static final String NAME = "일상";

    @Test
    @DisplayName("카테고리 생성")
    void testAddCategory() {
        // given
        Category category = Category.of(NAME);
        when(categoryRepository.save(any(Category.class))).thenReturn(category);

        // when
        CategoryResponse result = categoryService.addCategory(NAME);

        // then
        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo(NAME);
    }
}
