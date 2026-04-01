package com.sun.note.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.sun.note.config.SecurityConfig;
import com.sun.note.dto.CategoryResponse;
import com.sun.note.filter.JwtFilter;
import com.sun.note.service.CategoryNoteService;
import com.sun.note.service.CategoryService;
import com.sun.note.exception.BusinessException;
import com.sun.note.exception.ErrorCode;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = CategoryController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                SecurityConfig.class, JwtFilter.class }))
@WithMockUser
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private CategoryNoteService categoryNoteService;

    private static final String NAME = "일상";

    private CategoryResponse createCategoryResponse() {
        return CategoryResponse.of(1L, NAME);
    }

    @Nested
    @DisplayName("POST /api/categories - 카테고리 생성")
    class AddCategory {

        @Test
        @DisplayName("유효한 요청이면 201 Created 반환")
        void addCategory_success() throws Exception {
            when(categoryService.addCategory(any(String.class)))
                    .thenReturn(createCategoryResponse());

            String body = """
                    {
                        "name": "%s"
                    }
                    """.formatted(NAME);

            mockMvc.perform(post("/api/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value(NAME));
        }
    }

    @Nested
    @DisplayName("PATCH /api/categories/{id} - 카테고리 수정")
    class EditCategory {

        @Test
        @DisplayName("유효한 요청이면 200 OK 반환")
        void editCategory_success() throws Exception {
            CategoryResponse response = CategoryResponse.of(1L, "개발");
            when(categoryService.editCategory(eq(1L), eq("개발")))
                    .thenReturn(response);

            String body = """
                    {
                        "name": "개발"
                    }
                    """;

            mockMvc.perform(patch("/api/categories/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("개발"));
        }

        @Test
        @DisplayName("존재하지 않는 카테고리면 404 반환")
        void editCategory_notFound() throws Exception {
            when(categoryService.editCategory(eq(999L), any()))
                    .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

            String body = """
                    {
                        "name": "개발"
                    }
                    """;

            mockMvc.perform(patch("/api/categories/999")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /api/categories/{id} - Validation")
    class EditCategoryValidation {

        @Test
        @DisplayName("name이 빈 문자열이면 400 반환")
        void blankName() throws Exception {
            String body = """
                    {
                        "name": "   "
                    }
                    """;

            mockMvc.perform(patch("/api/categories/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("name이 null이면 400 반환")
        void nullName() throws Exception {
            String body = """
                    {}
                    """;

            mockMvc.perform(patch("/api/categories/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("name이 100자면 통과")
        void nameAtMaxLength() throws Exception {
            String name = "a".repeat(100);
            when(categoryService.editCategory(eq(1L), any(String.class)))
                    .thenReturn(CategoryResponse.of(1L, name));

            String body = """
                    {
                        "name": "%s"
                    }
                    """.formatted(name);

            mockMvc.perform(patch("/api/categories/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("name이 101자면 400 반환")
        void nameExceedsMaxLength() throws Exception {
            String name = "a".repeat(101);

            String body = """
                    {
                        "name": "%s"
                    }
                    """.formatted(name);

            mockMvc.perform(patch("/api/categories/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("요청 본문이 비어있으면 400 반환")
        void emptyBody() throws Exception {
            mockMvc.perform(patch("/api/categories/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/categories - Validation")
    class AddCategoryValidation {

        @Test
        @DisplayName("name이 빈 문자열이면 400 반환")
        void blankName() throws Exception {
            String body = """
                    {
                        "name": "   "
                    }
                    """;

            mockMvc.perform(post("/api/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("name이 null이면 400 반환")
        void nullName() throws Exception {
            String body = """
                    {}
                    """;

            mockMvc.perform(post("/api/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("name이 100자면 통과")
        void nameAtMaxLength() throws Exception {
            String name = "a".repeat(100);
            when(categoryService.addCategory(any(String.class)))
                    .thenReturn(CategoryResponse.of(1L, name));

            String body = """
                    {
                        "name": "%s"
                    }
                    """.formatted(name);

            mockMvc.perform(post("/api/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("name이 101자면 400 반환")
        void nameExceedsMaxLength() throws Exception {
            String name = "a".repeat(101);

            String body = """
                    {
                        "name": "%s"
                    }
                    """.formatted(name);

            mockMvc.perform(post("/api/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("요청 본문이 비어있으면 400 반환")
        void emptyBody() throws Exception {
            mockMvc.perform(post("/api/categories")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }
    }
}
