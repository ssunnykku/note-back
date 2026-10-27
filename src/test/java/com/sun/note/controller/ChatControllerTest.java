package com.sun.note.controller;

import java.time.LocalDateTime;
import java.util.List;
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
import com.sun.note.domain.entity.ChatRole;
import com.sun.note.dto.ChatMessageResponse;
import com.sun.note.dto.ChatRoomResponse;
import com.sun.note.exception.BusinessException;
import com.sun.note.exception.ErrorCode;
import com.sun.note.filter.JwtFilter;
import com.sun.note.service.ChatService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    @DisplayName("GET /api/chats - 채팅 목록 조회")
    class GetChatRooms {

        @Test
        @DisplayName("본인 채팅 목록을 200 OK로 반환")
        void getChatRooms_success() throws Exception {
            when(chatService.getChatRooms(USER_ID))
                    .thenReturn(List.of(ChatRoomResponse.of(CHAT_ID, "안녕?", MODEL_ID, "안녕하세요!", NOW)));

            mockMvc.perform(get("/api/chats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(CHAT_ID))
                    .andExpect(jsonPath("$[0].title").value("안녕?"))
                    .andExpect(jsonPath("$[0].modelId").value(MODEL_ID))
                    .andExpect(jsonPath("$[0].lastMessage").value("안녕하세요!"))
                    .andExpect(jsonPath("$[0].createdAt").exists());
        }

        @Test
        @DisplayName("채팅이 없으면 빈 배열 반환")
        void getChatRooms_empty() throws Exception {
            when(chatService.getChatRooms(USER_ID)).thenReturn(List.of());

            mockMvc.perform(get("/api/chats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("POST /api/chats - 채팅방 생성")
    class CreateChatRoom {

        @Test
        @DisplayName("modelId와 함께 생성하면 201 Created와 발급된 id 반환")
        void createChatRoom_withModel() throws Exception {
            when(chatService.createChatRoom(USER_ID, MODEL_ID))
                    .thenReturn(ChatRoomResponse.of(1L, null, MODEL_ID, null, NOW));

            mockMvc.perform(post("/api/chats")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"modelId\": \"%s\"}".formatted(MODEL_ID)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.modelId").value(MODEL_ID))
                    .andExpect(jsonPath("$.title").isEmpty());
        }

        @Test
        @DisplayName("body 없이 생성하면 modelId 없이 201 Created")
        void createChatRoom_withoutBody() throws Exception {
            when(chatService.createChatRoom(USER_ID, null))
                    .thenReturn(ChatRoomResponse.of(1L, null, null, null, NOW));

            mockMvc.perform(post("/api/chats").with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("modelId가 100자를 넘으면 400 반환")
        void createChatRoom_modelIdTooLong() throws Exception {
            mockMvc.perform(post("/api/chats")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"modelId\": \"%s\"}".formatted("a".repeat(101))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/chats/{chatId}/messages - 메시지 조회")
    class GetMessages {

        @Test
        @DisplayName("메시지 목록을 200 OK로 반환")
        void getMessages_success() throws Exception {
            when(chatService.getMessages(USER_ID, CHAT_ID)).thenReturn(List.of(
                    ChatMessageResponse.of(1L, ChatRole.USER, "안녕?", NOW),
                    ChatMessageResponse.of(2L, ChatRole.AI, "안녕하세요!", NOW)));

            mockMvc.perform(get("/api/chats/{chatId}/messages", CHAT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].role").value("USER"))
                    .andExpect(jsonPath("$[0].content").value("안녕?"))
                    .andExpect(jsonPath("$[1].role").value("AI"))
                    .andExpect(jsonPath("$[1].createdAt").exists());
        }

        @Test
        @DisplayName("chatId가 숫자가 아니면 400 반환")
        void getMessages_invalidChatId() throws Exception {
            mockMvc.perform(get("/api/chats/{chatId}/messages", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("다른 사용자의 채팅방이면 404 반환")
        void getMessages_otherUsersRoom() throws Exception {
            when(chatService.getMessages(USER_ID, CHAT_ID))
                    .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

            mockMvc.perform(get("/api/chats/{chatId}/messages", CHAT_ID))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/chats/{chatId}/messages - 메시지 전송")
    class SendMessage {

        @Test
        @DisplayName("유효한 요청이면 201 Created 반환")
        void sendMessage_success() throws Exception {
            when(chatService.sendMessage(eq(USER_ID), eq(CHAT_ID), eq("안녕?"), eq(MODEL_ID)))
                    .thenReturn(ChatMessageResponse.of(1L, ChatRole.AI, "안녕하세요!", NOW));

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
                    .andExpect(jsonPath("$.role").value("AI"))
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

        @Test
        @DisplayName("같은 채팅방이 응답 대기 중이면 409 CHAT_IN_PROGRESS 반환")
        void sendMessage_inProgress() throws Exception {
            when(chatService.sendMessage(eq(USER_ID), eq(CHAT_ID), any(), any()))
                    .thenThrow(new BusinessException(ErrorCode.CHAT_IN_PROGRESS));

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
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("CHAT_IN_PROGRESS"));
        }

        @Test
        @DisplayName("전송 횟수 제한을 넘으면 429 CHAT_RATE_LIMITED 반환")
        void sendMessage_rateLimited() throws Exception {
            when(chatService.sendMessage(eq(USER_ID), eq(CHAT_ID), any(), any()))
                    .thenThrow(new BusinessException(ErrorCode.CHAT_RATE_LIMITED));

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
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.title").value("CHAT_RATE_LIMITED"));
        }
    }

    @Test
    @DisplayName("매핑되지 않은 경로는 500이 아니라 404 반환")
    void unmappedPath_returns404() throws Exception {
        mockMvc.perform(get("/api/chats/{chatId}", CHAT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("RESOURCE_NOT_FOUND"));
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
        @DisplayName("Content-Type이 json이 아니면 415 반환")
        void unsupportedContentType() throws Exception {
            mockMvc.perform(post("/api/chats/{chatId}/messages", CHAT_ID)
                    .with(csrf())
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{\"content\":\"hi\",\"modelId\":\"dev\"}"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.title").value("UNSUPPORTED_MEDIA_TYPE"));
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
