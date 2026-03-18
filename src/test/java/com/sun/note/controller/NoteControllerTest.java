package com.sun.note.controller;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.sun.note.dto.NoteResponse;
import com.sun.note.exception.BusinessException;
import com.sun.note.exception.ErrorCode;
import com.sun.note.service.NoteService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NoteController.class)
class NoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NoteService noteService;

    private static final UUID USER_ID = UUID.fromString("2013a306-9369-46ba-ac55-2f547ac5c50f");
    private static final Long CATEGORY_ID = 1L;
    private static final String TITLE = "첫번째 노트";
    private static final String CONTENT = "#내용";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 17, 12, 0, 0);

    private NoteResponse createNoteResponse() {
        return NoteResponse.of(1L, USER_ID, CATEGORY_ID, TITLE, CONTENT, NOW, NOW);
    }


    @Nested
    @DisplayName("POST /api/notes - 노트 생성")
    class AddNote {

        @Test
        @DisplayName("유효한 요청이면 201 Created 반환")
        void addNote_success() throws Exception {
            when(noteService.addNote(any(UUID.class), any(Long.class), any(String.class), any(String.class)))
                    .thenReturn(createNoteResponse());

            String body = """
                    {
                        "userId": "%s",
                        "categoryId": %d,
                        "title": "%s",
                        "content": "%s"
                    }
                    """.formatted(USER_ID, CATEGORY_ID, TITLE, CONTENT);

            mockMvc.perform(post("/api/notes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                    .andExpect(jsonPath("$.categoryId").value(CATEGORY_ID))
                    .andExpect(jsonPath("$.title").value(TITLE))
                    .andExpect(jsonPath("$.content").value(CONTENT));
        }
    }

    @Nested
    @DisplayName("PUT /api/notes/{id} - 노트 수정")
    class EditNote {

        @Test
        @DisplayName("유효한 요청이면 200 OK 반환")
        void editNote_success() throws Exception {
            NoteResponse response = NoteResponse.of(1L, USER_ID, 2L, "수정된 제목", "수정된 내용", NOW, NOW);
            when(noteService.editNote(eq(1L), eq(2L), eq("수정된 제목"), eq("수정된 내용")))
                    .thenReturn(response);

            String body = """
                    {
                        "categoryId": 2,
                        "title": "수정된 제목",
                        "content": "수정된 내용"
                    }
                    """;

            mockMvc.perform(put("/api/notes/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.title").value("수정된 제목"))
                    .andExpect(jsonPath("$.content").value("수정된 내용"))
                    .andExpect(jsonPath("$.categoryId").value(2))
                    .andExpect(jsonPath("$.createdAt").exists())
                    .andExpect(jsonPath("$.updatedAt").exists());
        }

        @Test
        @DisplayName("존재하지 않는 노트면 404 반환")
        void editNote_notFound() throws Exception {
            when(noteService.editNote(eq(999L), any(), any(), any()))
                    .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

            String body = """
                    {
                        "categoryId": 1,
                        "title": "제목",
                        "content": "내용"
                    }
                    """;

            mockMvc.perform(put("/api/notes/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/notes/{id} - 노트 조회")
    class GetNote {

        @Test
        @DisplayName("존재하는 노트면 200 OK 반환")
        void getNote_success() throws Exception {
            when(noteService.getById(1L)).thenReturn(createNoteResponse());

            mockMvc.perform(get("/api/notes/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.title").value(TITLE))
                    .andExpect(jsonPath("$.content").value(CONTENT));
        }

        @Test
        @DisplayName("존재하지 않는 노트면 404 반환")
        void getNote_notFound() throws Exception {
            when(noteService.getById(999L))
                    .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

            mockMvc.perform(get("/api/notes/999"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/notes/{id} - 노트 삭제")
    class DeleteNote {

        @Test
        @DisplayName("존재하는 노트면 204 No Content 반환")
        void deleteNote_success() throws Exception {
            doNothing().when(noteService).deleteNote(1L);

            mockMvc.perform(delete("/api/notes/1"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("존재하지 않는 노트면 404 반환")
        void deleteNote_notFound() throws Exception {
            doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                    .when(noteService).deleteNote(999L);

            mockMvc.perform(delete("/api/notes/999"))
                    .andExpect(status().isNotFound());
        }
    }

    // 경계값 / Validation 테스트

    @Nested
    @DisplayName("POST /api/notes - Validation")
    class AddNoteValidation {

        @Test
        @DisplayName("userId가 null이면 400 반환")
        void nullUserId() throws Exception {
            String body = """
                    {
                        "categoryId": 1,
                        "title": "제목",
                        "content": "내용"
                    }
                    """;

            mockMvc.perform(post("/api/notes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("categoryId가 null이면 400 반환")
        void nullCategoryId() throws Exception {
            String body = """
                    {
                        "userId": "%s",
                        "title": "제목",
                        "content": "내용"
                    }
                    """.formatted(USER_ID);

            mockMvc.perform(post("/api/notes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("title이 빈 문자열이면 400 반환")
        void blankTitle() throws Exception {
            String body = """
                    {
                        "userId": "%s",
                        "categoryId": 1,
                        "title": "   ",
                        "content": "내용"
                    }
                    """.formatted(USER_ID);

            mockMvc.perform(post("/api/notes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("content가 빈 문자열이면 400 반환")
        void blankContent() throws Exception {
            String body = """
                    {
                        "userId": "%s",
                        "categoryId": 1,
                        "title": "제목",
                        "content": "   "
                    }
                    """.formatted(USER_ID);

            mockMvc.perform(post("/api/notes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("title이 255자면 통과")
        void titleAtMaxLength() throws Exception {
            String title = "a".repeat(255);
            when(noteService.addNote(any(), any(), any(), any())).thenReturn(createNoteResponse());

            String body = """
                    {
                        "userId": "%s",
                        "categoryId": 1,
                        "title": "%s",
                        "content": "내용"
                    }
                    """.formatted(USER_ID, title);

            mockMvc.perform(post("/api/notes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("title이 256자면 400 반환")
        void titleExceedsMaxLength() throws Exception {
            String title = "a".repeat(256);

            String body = """
                    {
                        "userId": "%s",
                        "categoryId": 1,
                        "title": "%s",
                        "content": "내용"
                    }
                    """.formatted(USER_ID, title);

            mockMvc.perform(post("/api/notes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("요청 본문이 비어있으면 400 반환")
        void emptyBody() throws Exception {
            mockMvc.perform(post("/api/notes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/notes/{id} - Validation")
    class EditNoteValidation {

        @Test
        @DisplayName("title이 빈 문자열이면 400 반환")
        void blankTitle() throws Exception {
            String body = """
                    {
                        "categoryId": 1,
                        "title": "   ",
                        "content": "내용"
                    }
                    """;

            mockMvc.perform(put("/api/notes/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("content가 빈 문자열이면 400 반환")
        void blankContent() throws Exception {
            String body = """
                    {
                        "categoryId": 1,
                        "title": "제목",
                        "content": "   "
                    }
                    """;

            mockMvc.perform(put("/api/notes/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("title이 256자면 400 반환")
        void titleExceedsMaxLength() throws Exception {
            String title = "a".repeat(256);

            String body = """
                    {
                        "categoryId": 1,
                        "title": "%s",
                        "content": "내용"
                    }
                    """.formatted(title);

            mockMvc.perform(put("/api/notes/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("categoryId가 null이어도 통과 (선택 필드)")
        void nullCategoryId_allowed() throws Exception {
            NoteResponse response = NoteResponse.of(1L, USER_ID, null, "제목", "내용", NOW, NOW);
            when(noteService.editNote(eq(1L), eq(null), eq("제목"), eq("내용")))
                    .thenReturn(response);

            String body = """
                    {
                        "title": "제목",
                        "content": "내용"
                    }
                    """;

            mockMvc.perform(put("/api/notes/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }
    }
}
