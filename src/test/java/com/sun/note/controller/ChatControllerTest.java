package com.sun.note.controller;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.sun.note.config.SecurityConfig;
import com.sun.note.dto.ChatMessageResponse;
import com.sun.note.exception.BusinessException;
import com.sun.note.exception.ErrorCode;
import com.sun.note.filter.JwtFilter;
import com.sun.note.service.ChatService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = ChatController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        SecurityConfig.class, JwtFilter.class }))
@WithMockUser(username = "2013a306-9369-46ba-ac55-2f547ac5c50f")
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    private static final UUID USER_ID = UUID.fromString("2013a306-9369-46ba-ac55-2f547ac5c50f");
    private static final Long CHAT_ID = 1700000000000L;
    private static final String MODEL_ID = "qwen3:8b";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 12, 0, 0);

    @Nested
    @DisplayName("POST /api/chats/{chatId}/messages - 메시지 전송")
    class SendMessage {

        @Test
        @DisplayName("유효한 요청이면 201 Created 반환")
        void sendMessage_success() throws Exception {
            when(chatService.sendMessage(eq(USER_ID), eq(CHAT_ID), eq("안녕?"), eq(MODEL_ID)))
                    .thenReturn(ChatMessageResponse.of(1L, "안녕하세요!", NOW));

            String body = """
                    {
                        "content": "안녕?",
                        "modelId": "%s"
                    }
                    """.formatted(MODEL_ID);

            mockMvc.perform(post("/api/chats/{chatId}/messages", CHAT_ID)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.content").value("안녕하세요!"))
                    .andExpect(jsonPath("$.createdAt").exists());
        }

        @Test
        @DisplayName("다른 사용자의 채팅방이면 404 반환")
        void sendMessage_otherUsersRoom() throws Exception {
            when(chatService.sendMessage(eq(USER_ID), eq(CHAT_ID), any(), any()))
                    .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

            String body = """
                    {
                        "content": "안녕?",
                        "modelId": "%s"
                    }
                    """.formatted(MODEL_ID);

            mockMvc.perform(post("/api/chats/{chatId}/messages", CHAT_ID)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("AI 서비스 연결 실패 시 503 반환")
        void sendMessage_aiServiceUnavailable() throws Exception {
            when(chatService.sendMessage(eq(USER_ID), eq(CHAT_ID), any(), any()))
                    .thenThrow(new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE));

            String body = """
                    {
                        "content": "안녕?",
                        "modelId": "%s"
                    }
                    """.formatted(MODEL_ID);

            mockMvc.perform(post("/api/chats/{chatId}/messages", CHAT_ID)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isServiceUnavailable());
        }
    }

    @Nested
    @DisplayName("POST /api/chats/{chatId}/messages - Validation")
    class SendMessageValidation {

        @Test
        @DisplayName("content가 빈 문자열이면 400 반환")
        void blankContent() throws Exception {
            String body = """
                    {
                        "content": "   ",
                        "modelId": "%s"
                    }
                    """.formatted(MODEL_ID);

            mockMvc.perform(post("/api/chats/{chatId}/messages", CHAT_ID)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("modelId가 없으면 400 반환")
        void blankModelId() throws Exception {
            String body = """
                    {
                        "content": "안녕?"
                    }
                    """;

            mockMvc.perform(post("/api/chats/{chatId}/messages", CHAT_ID)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest());
        }
    }
}
