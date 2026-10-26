package com.sun.note.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sun.note.client.OllamaClient;
import com.sun.note.client.OllamaMessage;
import com.sun.note.domain.entity.ChatMessage;
import com.sun.note.domain.entity.ChatRole;
import com.sun.note.domain.entity.ChatRoom;
import com.sun.note.dto.ChatMessageResponse;
import com.sun.note.exception.BusinessException;
import com.sun.note.exception.ErrorCode;
import com.sun.note.repository.ChatMessageRepository;
import com.sun.note.repository.ChatRoomRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private OllamaClient ollamaClient;

    @InjectMocks
    private ChatService chatService;

    private static final UUID USER_ID = UUID.fromString("2013a306-9369-46ba-ac55-2f547ac5c50f");
    private static final UUID OTHER_USER_ID = UUID.fromString("9c4f7b3e-1234-4a5b-9c1d-abcdefabcdef");
    private static final Long CHAT_ROOM_ID = 1700000000000L;
    private static final String MODEL_ID = "qwen3:8b";
    private static final String CONTENT = "안녕?";
    private static final String AI_CONTENT = "안녕하세요!";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 12, 0, 0);

    @Test
    @DisplayName("존재하지 않는 채팅방이면 생성 후 메시지를 전송한다")
    void sendMessage_createsRoom_whenNotExists() {
        // given
        when(chatRoomRepository.findById(CHAT_ROOM_ID)).thenReturn(Optional.empty());
        when(chatRoomRepository.save(any(ChatRoom.class)))
                .thenReturn(ChatRoom.of(CHAT_ROOM_ID, USER_ID, MODEL_ID));
        when(chatMessageRepository.findByChatRoomIdOrderByCreatedAtAsc(CHAT_ROOM_ID)).thenReturn(List.of());
        when(ollamaClient.chat(eq(MODEL_ID), any())).thenReturn(AI_CONTENT);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            if (message.getRole() == ChatRole.AI) {
                return ChatMessage.of(10L, CHAT_ROOM_ID, ChatRole.AI, AI_CONTENT, NOW);
            }
            return message;
        });

        // when
        ChatMessageResponse response = chatService.sendMessage(USER_ID, CHAT_ROOM_ID, CONTENT, MODEL_ID);

        // then
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.content()).isEqualTo(AI_CONTENT);
        assertThat(response.createdAt()).isEqualTo(NOW);
        verify(chatRoomRepository).save(any(ChatRoom.class));
    }

    @Test
    @DisplayName("기존 채팅방이면 새로 만들지 않고 메시지 히스토리를 함께 전달한다")
    void sendMessage_usesExistingRoom_andIncludesHistory() {
        // given
        ChatRoom existingRoom = ChatRoom.of(CHAT_ROOM_ID, USER_ID, MODEL_ID);
        ChatMessage previousUserMsg = ChatMessage.of(1L, CHAT_ROOM_ID, ChatRole.USER, "이전 질문", NOW.minusMinutes(1));
        ChatMessage previousAiMsg = ChatMessage.of(2L, CHAT_ROOM_ID, ChatRole.AI, "이전 답변", NOW.minusSeconds(30));

        when(chatRoomRepository.findById(CHAT_ROOM_ID)).thenReturn(Optional.of(existingRoom));
        when(chatMessageRepository.findByChatRoomIdOrderByCreatedAtAsc(CHAT_ROOM_ID))
                .thenReturn(List.of(previousUserMsg, previousAiMsg));
        when(ollamaClient.chat(eq(MODEL_ID), any())).thenReturn(AI_CONTENT);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            if (message.getRole() == ChatRole.AI) {
                return ChatMessage.of(10L, CHAT_ROOM_ID, ChatRole.AI, AI_CONTENT, NOW);
            }
            return message;
        });

        // when
        chatService.sendMessage(USER_ID, CHAT_ROOM_ID, CONTENT, MODEL_ID);

        // then
        verify(chatRoomRepository, never()).save(any(ChatRoom.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OllamaMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(ollamaClient).chat(eq(MODEL_ID), messagesCaptor.capture());

        List<OllamaMessage> sentMessages = messagesCaptor.getValue();
        assertThat(sentMessages).hasSize(3);
        assertThat(sentMessages.get(0).content()).isEqualTo("이전 질문");
        assertThat(sentMessages.get(1).content()).isEqualTo("이전 답변");
        assertThat(sentMessages.get(1).role()).isEqualTo("assistant");
        assertThat(sentMessages.get(2).content()).isEqualTo(CONTENT);
        assertThat(sentMessages.get(2).role()).isEqualTo("user");
    }

    @Test
    @DisplayName("다른 사용자의 채팅방이면 예외처리")
    void sendMessage_otherUsersRoom_throws() {
        // given
        ChatRoom othersRoom = ChatRoom.of(CHAT_ROOM_ID, OTHER_USER_ID, MODEL_ID);
        when(chatRoomRepository.findById(CHAT_ROOM_ID)).thenReturn(Optional.of(othersRoom));

        // then
        assertThatThrownBy(() -> chatService.sendMessage(USER_ID, CHAT_ROOM_ID, CONTENT, MODEL_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.RESOURCE_NOT_FOUND.getMessage());

        verify(ollamaClient, never()).chat(anyString(), any());
    }

    @Test
    @DisplayName("Ollama 호출 실패 시 예외가 그대로 전파된다")
    void sendMessage_ollamaFails_propagatesException() {
        // given
        when(chatRoomRepository.findById(CHAT_ROOM_ID))
                .thenReturn(Optional.of(ChatRoom.of(CHAT_ROOM_ID, USER_ID, MODEL_ID)));
        when(chatMessageRepository.findByChatRoomIdOrderByCreatedAtAsc(CHAT_ROOM_ID)).thenReturn(List.of());
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ollamaClient.chat(eq(MODEL_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE));

        // then
        assertThatThrownBy(() -> chatService.sendMessage(USER_ID, CHAT_ROOM_ID, CONTENT, MODEL_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.AI_SERVICE_UNAVAILABLE.getMessage());
    }
}
