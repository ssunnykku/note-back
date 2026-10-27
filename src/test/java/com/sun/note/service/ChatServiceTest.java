package com.sun.note.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sun.note.client.OllamaClient;
import com.sun.note.client.OllamaMessage;
import com.sun.note.config.ChatProperties;
import com.sun.note.domain.entity.ChatMessage;
import com.sun.note.domain.entity.ChatRole;
import com.sun.note.domain.entity.ChatRoom;
import com.sun.note.dto.ChatMessageResponse;
import com.sun.note.dto.ChatRoomResponse;
import com.sun.note.exception.BusinessException;
import com.sun.note.exception.ErrorCode;
import com.sun.note.repository.ChatMessageRepository;
import com.sun.note.repository.ChatRoomRepository;
import com.sun.note.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
    private UserRepository userRepository;

    @Mock
    private OllamaClient ollamaClient;

    // 이전 대화는 최근 4개 / 100자까지, 전송은 2분에 3회까지
    @Spy
    private ChatProperties chatProperties = new ChatProperties(
            new ChatProperties.History(4, 100),
            new ChatProperties.RateLimit(3, Duration.ofMinutes(2)));

    // 실제 트랜잭션 없이 콜백만 실행되도록 mock 트랜잭션 매니저를 사용한다.
    @Spy
    private TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));

    @InjectMocks
    private ChatService chatService;

    private static final UUID USER_ID = UUID.fromString("2013a306-9369-46ba-ac55-2f547ac5c50f");
    private static final UUID OTHER_USER_ID = UUID.fromString("9c4f7b3e-1234-4a5b-9c1d-abcdefabcdef");
    private static final Long CHAT_ROOM_ID = 1700000000000L;
    private static final String MODEL_ID = "qwen3:8b";
    private static final String CONTENT = "안녕?";
    private static final String AI_CONTENT = "안녕하세요!";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 12, 0, 0);

    @BeforeEach
    void setUp() {
        // 전송 잠금 TTL 계산에 쓰인다. (connect 5s + read 120s)
        lenient().when(ollamaClient.getMaxCallDuration()).thenReturn(Duration.ofSeconds(125));
    }

    @Test
    @DisplayName("채팅방 생성 - 서버가 발급한 id로 빈 채팅방을 반환한다")
    void createChatRoom() {
        // given
        when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(ChatRoom.of(CHAT_ROOM_ID, USER_ID, MODEL_ID));

        // when
        ChatRoomResponse response = chatService.createChatRoom(USER_ID, MODEL_ID);

        // then
        ArgumentCaptor<ChatRoom> captor = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isNull();
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);

        assertThat(response.id()).isEqualTo(CHAT_ROOM_ID);
        assertThat(response.modelId()).isEqualTo(MODEL_ID);
        assertThat(response.title()).isNull();
        assertThat(response.lastMessage()).isNull();
    }

    @Test
    @DisplayName("채팅방 생성 - 빈 modelId는 null(모델 미지정)로 저장한다")
    void createChatRoom_blankModelId_savedAsNull() {
        // given
        when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(ChatRoom.of(CHAT_ROOM_ID, USER_ID, null));

        // when
        chatService.createChatRoom(USER_ID, "  ");

        // then
        ArgumentCaptor<ChatRoom> captor = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomRepository).save(captor.capture());
        assertThat(captor.getValue().getModelId()).isNull();
    }

    @Test
    @DisplayName("채팅방 생성 - modelId 앞뒤 공백은 지워서 저장한다")
    void createChatRoom_trimsModelId() {
        // given
        when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(ChatRoom.of(CHAT_ROOM_ID, USER_ID, MODEL_ID));

        // when
        chatService.createChatRoom(USER_ID, " " + MODEL_ID + " ");

        // then
        ArgumentCaptor<ChatRoom> captor = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomRepository).save(captor.capture());
        assertThat(captor.getValue().getModelId()).isEqualTo(MODEL_ID);
    }

    @Test
    @DisplayName("전송 - modelId 앞뒤 공백을 지워 Ollama 호출과 모델 갱신에 사용한다")
    void sendMessage_trimsModelId() {
        // given
        givenLockAcquired();
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenReturn(ChatMessage.of(3L, CHAT_ROOM_ID, ChatRole.USER, CONTENT, NOW));
        when(ollamaClient.chat(eq(MODEL_ID), any())).thenReturn(AI_CONTENT);

        // when
        chatService.sendMessage(USER_ID, CHAT_ROOM_ID, CONTENT, " " + MODEL_ID + "\t");

        // then
        verify(ollamaClient).chat(eq(MODEL_ID), any());
        verify(chatRoomRepository).completeSend(eq(CHAT_ROOM_ID), any(), eq(MODEL_ID), any());
    }

    // 전송 잠금 획득 성공 상황을 만든다.
    private void givenLockAcquired() {
        when(chatRoomRepository.acquireSendLock(eq(CHAT_ROOM_ID), eq(USER_ID), any(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("존재하지 않는 채팅방이면 생성하지 않고 404")
    void sendMessage_roomNotExists_throws() {
        // given
        when(chatRoomRepository.acquireSendLock(eq(CHAT_ROOM_ID), eq(USER_ID), any(), any())).thenReturn(0);
        when(chatRoomRepository.findById(CHAT_ROOM_ID)).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> chatService.sendMessage(USER_ID, CHAT_ROOM_ID, CONTENT, MODEL_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.RESOURCE_NOT_FOUND.getMessage());

        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
        verify(ollamaClient, never()).chat(anyString(), any());
    }

    @Test
    @DisplayName("다른 사용자의 채팅방이면 404")
    void sendMessage_otherUsersRoom_throws() {
        // given
        when(chatRoomRepository.acquireSendLock(eq(CHAT_ROOM_ID), eq(USER_ID), any(), any())).thenReturn(0);
        when(chatRoomRepository.findById(CHAT_ROOM_ID))
                .thenReturn(Optional.of(ChatRoom.of(CHAT_ROOM_ID, OTHER_USER_ID, MODEL_ID)));

        // then
        assertThatThrownBy(() -> chatService.sendMessage(USER_ID, CHAT_ROOM_ID, CONTENT, MODEL_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.RESOURCE_NOT_FOUND.getMessage());

        verify(ollamaClient, never()).chat(anyString(), any());
    }

    @Test
    @DisplayName("본인 채팅방이 응답 대기 중(잠금)이면 409, 메시지 저장/AI 호출 안 함")
    void sendMessage_inProgress_throwsConflict() {
        // given
        when(chatRoomRepository.acquireSendLock(eq(CHAT_ROOM_ID), eq(USER_ID), any(), any())).thenReturn(0);
        when(chatRoomRepository.findById(CHAT_ROOM_ID))
                .thenReturn(Optional.of(ChatRoom.of(CHAT_ROOM_ID, USER_ID, MODEL_ID)));

        // then
        assertThatThrownBy(() -> chatService.sendMessage(USER_ID, CHAT_ROOM_ID, CONTENT, MODEL_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.CHAT_IN_PROGRESS.getMessage());

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
        verify(ollamaClient, never()).chat(anyString(), any());
    }

    @Test
    @DisplayName("성공 - 히스토리를 전달하고, AI 응답 저장과 함께 모델 갱신/잠금 해제한다")
    void sendMessage_success() {
        // given
        givenLockAcquired();
        ChatMessage previousUserMsg = ChatMessage.of(1L, CHAT_ROOM_ID, ChatRole.USER, "이전 질문", NOW.minusMinutes(1));
        ChatMessage previousAiMsg = ChatMessage.of(2L, CHAT_ROOM_ID, ChatRole.AI, "이전 답변", NOW.minusSeconds(30));
        when(chatMessageRepository.findByChatRoomIdOrderByCreatedAtDescIdDesc(eq(CHAT_ROOM_ID), any()))
                .thenReturn(List.of(previousAiMsg, previousUserMsg));
        when(ollamaClient.chat(eq("other-model"), any())).thenReturn(AI_CONTENT);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            return message.getRole() == ChatRole.AI
                    ? ChatMessage.of(10L, CHAT_ROOM_ID, ChatRole.AI, AI_CONTENT, NOW)
                    : ChatMessage.of(3L, CHAT_ROOM_ID, ChatRole.USER, CONTENT, NOW);
        });

        // when
        ChatMessageResponse response = chatService.sendMessage(USER_ID, CHAT_ROOM_ID, CONTENT, "other-model");

        // then
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.role()).isEqualTo(ChatRole.AI);
        assertThat(response.content()).isEqualTo(AI_CONTENT);
        assertThat(response.createdAt()).isEqualTo(NOW);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OllamaMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(ollamaClient).chat(eq("other-model"), messagesCaptor.capture());
        assertThat(messagesCaptor.getValue())
                .extracting(OllamaMessage::role, OllamaMessage::content)
                .containsExactly(
                        tuple("user", "이전 질문"),
                        tuple("assistant", "이전 답변"),
                        tuple("user", CONTENT));

        // 잠금 획득 때 쓴 만료 시각으로 해제해야 한다. (다른 요청의 잠금을 풀지 않도록)
        ArgumentCaptor<LocalDateTime> lockUntilCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(chatRoomRepository).acquireSendLock(eq(CHAT_ROOM_ID), eq(USER_ID), any(), lockUntilCaptor.capture());
        verify(chatRoomRepository).completeSend(eq(CHAT_ROOM_ID), eq(lockUntilCaptor.getValue()), eq("other-model"), any());
        verify(chatRoomRepository, never()).releaseSendLock(any(), any());
        verify(chatMessageRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("사용자 행을 잠근 뒤 전송 잠금을 획득한다 (사용자 단위 직렬화)")
    void sendMessage_locksUserBeforeAcquiringSendLock() {
        // given
        givenLockAcquired();
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenReturn(ChatMessage.of(3L, CHAT_ROOM_ID, ChatRole.USER, CONTENT, NOW));
        when(ollamaClient.chat(eq(MODEL_ID), any())).thenReturn(AI_CONTENT);

        // when
        chatService.sendMessage(USER_ID, CHAT_ROOM_ID, CONTENT, MODEL_ID);

        // then
        var inOrder = org.mockito.Mockito.inOrder(userRepository, chatRoomRepository);
        inOrder.verify(userRepository).lockForChat(USER_ID);
        inOrder.verify(chatRoomRepository).acquireSendLock(eq(CHAT_ROOM_ID), eq(USER_ID), any(), any());
    }

    @Test
    @DisplayName("2분 안에 보낸 메시지가 제한(3회) 이상이면 429, 메시지 저장/AI 호출 안 함")
    void sendMessage_rateLimited_throws() {
        // given
        givenLockAcquired();
        when(chatMessageRepository.countByUserIdAndRoleSince(eq(USER_ID), eq(ChatRole.USER), any())).thenReturn(3L);

        // then
        assertThatThrownBy(() -> chatService.sendMessage(USER_ID, CHAT_ROOM_ID, CONTENT, MODEL_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.CHAT_RATE_LIMITED.getMessage());

        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(chatMessageRepository).countByUserIdAndRoleSince(eq(USER_ID), eq(ChatRole.USER), sinceCaptor.capture());
        assertThat(sinceCaptor.getValue()).isBetween(
                LocalDateTime.now().minusMinutes(2).minusSeconds(5), LocalDateTime.now().minusMinutes(2));
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
        verify(ollamaClient, never()).chat(anyString(), any());
    }

    @Test
    @DisplayName("이전 대화는 최근 N개만 보내고, 맨 앞이 AI 답변이면 뺀다")
    void sendMessage_historyLimitedByCount() {
        // given: 오래된 순 u1 a1 u2 a2 u3 a3 → 최근 4개(u2 a2 u3 a3)
        givenLockAcquired();
        List<ChatMessage> desc = new ArrayList<>(List.of(
                ChatMessage.of(1L, CHAT_ROOM_ID, ChatRole.USER, "u1", NOW),
                ChatMessage.of(2L, CHAT_ROOM_ID, ChatRole.AI, "a1", NOW),
                ChatMessage.of(3L, CHAT_ROOM_ID, ChatRole.USER, "u2", NOW),
                ChatMessage.of(4L, CHAT_ROOM_ID, ChatRole.AI, "a2", NOW),
                ChatMessage.of(5L, CHAT_ROOM_ID, ChatRole.USER, "u3", NOW),
                ChatMessage.of(6L, CHAT_ROOM_ID, ChatRole.AI, "a3", NOW)));
        Collections.reverse(desc);
        when(chatMessageRepository.findByChatRoomIdOrderByCreatedAtDescIdDesc(eq(CHAT_ROOM_ID), any()))
                .thenAnswer(invocation -> desc.subList(0, invocation.<org.springframework.data.domain.Pageable>getArgument(1).getPageSize()));
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenReturn(ChatMessage.of(7L, CHAT_ROOM_ID, ChatRole.USER, CONTENT, NOW));
        when(ollamaClient.chat(eq(MODEL_ID), any())).thenReturn(AI_CONTENT);

        // when
        chatService.sendMessage(USER_ID, CHAT_ROOM_ID, CONTENT, MODEL_ID);

        // then
        assertThat(sentToOllama()).extracting(OllamaMessage::content).containsExactly("u2", "a2", "u3", "a3", CONTENT);
    }

    @Test
    @DisplayName("이전 대화는 글자 수 합계(새 메시지 포함)가 제한 안에 드는 만큼만 보내고, 맨 앞 AI 답변은 뺀다")
    void sendMessage_historyLimitedByChars() {
        // given: 제한 100자, 새 메시지 3자 → 이전 대화 97자까지. 최근부터 a2(40) u2(40)까지 80자, a1(40)은 초과
        givenLockAcquired();
        when(chatMessageRepository.findByChatRoomIdOrderByCreatedAtDescIdDesc(eq(CHAT_ROOM_ID), any()))
                .thenReturn(List.of(
                        ChatMessage.of(4L, CHAT_ROOM_ID, ChatRole.AI, "d".repeat(40), NOW),
                        ChatMessage.of(3L, CHAT_ROOM_ID, ChatRole.USER, "c".repeat(40), NOW),
                        ChatMessage.of(2L, CHAT_ROOM_ID, ChatRole.AI, "b".repeat(40), NOW),
                        ChatMessage.of(1L, CHAT_ROOM_ID, ChatRole.USER, "a".repeat(10), NOW)));
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenReturn(ChatMessage.of(5L, CHAT_ROOM_ID, ChatRole.USER, CONTENT, NOW));
        when(ollamaClient.chat(eq(MODEL_ID), any())).thenReturn(AI_CONTENT);

        // when
        chatService.sendMessage(USER_ID, CHAT_ROOM_ID, CONTENT, MODEL_ID);

        // then
        assertThat(sentToOllama()).extracting(OllamaMessage::content)
                .containsExactly("c".repeat(40), "d".repeat(40), CONTENT);
    }

    @Test
    @DisplayName("이전 대화 범위에서 남은 것이 AI 답변뿐이면 새 메시지만 보낸다")
    void sendMessage_historyOnlyAi_sendsOnlyNewMessage() {
        // given
        givenLockAcquired();
        when(chatMessageRepository.findByChatRoomIdOrderByCreatedAtDescIdDesc(eq(CHAT_ROOM_ID), any()))
                .thenReturn(List.of(
                        ChatMessage.of(2L, CHAT_ROOM_ID, ChatRole.AI, "답변", NOW),
                        ChatMessage.of(1L, CHAT_ROOM_ID, ChatRole.USER, "x".repeat(99), NOW)));
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenReturn(ChatMessage.of(3L, CHAT_ROOM_ID, ChatRole.USER, CONTENT, NOW));
        when(ollamaClient.chat(eq(MODEL_ID), any())).thenReturn(AI_CONTENT);

        // when
        chatService.sendMessage(USER_ID, CHAT_ROOM_ID, CONTENT, MODEL_ID);

        // then
        assertThat(sentToOllama()).extracting(OllamaMessage::role, OllamaMessage::content)
                .containsExactly(tuple("user", CONTENT));
    }

    private List<OllamaMessage> sentToOllama() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OllamaMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(ollamaClient).chat(anyString(), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Ollama 실패 - 사용자 메시지 삭제 + 잠금 해제, 모델 갱신 안 함, 예외 전파")
    void sendMessage_ollamaFails_rollsBack() {
        // given
        givenLockAcquired();
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenReturn(ChatMessage.of(7L, CHAT_ROOM_ID, ChatRole.USER, CONTENT, NOW));
        when(ollamaClient.chat(eq(MODEL_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE));

        // then
        assertThatThrownBy(() -> chatService.sendMessage(USER_ID, CHAT_ROOM_ID, CONTENT, MODEL_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.AI_SERVICE_UNAVAILABLE.getMessage());

        verify(chatMessageRepository).deleteById(7L);
        verify(chatRoomRepository).releaseSendLock(eq(CHAT_ROOM_ID), any());
        verify(chatRoomRepository, never()).completeSend(any(), any(), any(), any());
        verify(chatMessageRepository).save(any(ChatMessage.class)); // 사용자 메시지 1건만 저장
    }

    @Test
    @DisplayName("AI 응답 저장(tx2) 실패 - 사용자 메시지 삭제 + 잠금 해제, 원래 예외 전파")
    void sendMessage_saveAiFails_rollsBack() {
        // given
        givenLockAcquired();
        when(ollamaClient.chat(eq(MODEL_ID), any())).thenReturn(AI_CONTENT);
        RuntimeException dbFailure = new RuntimeException("db down");
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            if (message.getRole() == ChatRole.AI) {
                throw dbFailure;
            }
            return ChatMessage.of(7L, CHAT_ROOM_ID, ChatRole.USER, CONTENT, NOW);
        });

        // then
        assertThatThrownBy(() -> chatService.sendMessage(USER_ID, CHAT_ROOM_ID, CONTENT, MODEL_ID))
                .isSameAs(dbFailure);

        verify(chatMessageRepository).deleteById(7L);
        verify(chatRoomRepository).releaseSendLock(eq(CHAT_ROOM_ID), any());
    }

    @Test
    @DisplayName("Error(OOM 등)가 나도 사용자 메시지 삭제 + 잠금 해제 후 그대로 전파한다")
    void sendMessage_error_rollsBack() {
        // given
        givenLockAcquired();
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenReturn(ChatMessage.of(7L, CHAT_ROOM_ID, ChatRole.USER, CONTENT, NOW));
        OutOfMemoryError error = new OutOfMemoryError("test");
        when(ollamaClient.chat(eq(MODEL_ID), any())).thenThrow(error);

        // then
        assertThatThrownBy(() -> chatService.sendMessage(USER_ID, CHAT_ROOM_ID, CONTENT, MODEL_ID)).isSameAs(error);

        verify(chatMessageRepository).deleteById(7L);
        verify(chatRoomRepository).releaseSendLock(eq(CHAT_ROOM_ID), any());
    }

    @Test
    @DisplayName("Ollama 실패 후 보상 처리까지 실패해도 원래 예외(503)를 전파한다")
    void sendMessage_ollamaFails_andRollbackFails_stillThrowsOriginal() {
        // given
        givenLockAcquired();
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenReturn(ChatMessage.of(7L, CHAT_ROOM_ID, ChatRole.USER, CONTENT, NOW));
        when(ollamaClient.chat(eq(MODEL_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE));
        doThrow(new RuntimeException("db down")).when(chatMessageRepository).deleteById(7L);

        // then
        assertThatThrownBy(() -> chatService.sendMessage(USER_ID, CHAT_ROOM_ID, CONTENT, MODEL_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.AI_SERVICE_UNAVAILABLE.getMessage());
    }

    @Test
    @DisplayName("채팅 목록은 첫 사용자 메시지를 제목으로, 마지막 메시지를 미리보기로 반환한다")
    void getChatRooms_mapsTitleAndLastMessage() {
        // given
        Long otherRoomId = CHAT_ROOM_ID + 1;
        ChatRoom newerRoom = ChatRoom.of(otherRoomId, USER_ID, "study");
        ChatRoom olderRoom = ChatRoom.of(CHAT_ROOM_ID, USER_ID, MODEL_ID);
        String longQuestion = "가".repeat(120);

        when(chatRoomRepository.findByUserIdOrderByCreatedAtDescIdDesc(USER_ID)).thenReturn(List.of(newerRoom, olderRoom));
        when(chatMessageRepository.findFirstMessagesByChatRoomIds(List.of(otherRoomId, CHAT_ROOM_ID), ChatRole.USER))
                .thenReturn(List.of(
                        ChatMessage.of(1L, CHAT_ROOM_ID, ChatRole.USER, CONTENT, NOW),
                        ChatMessage.of(3L, otherRoomId, ChatRole.USER, longQuestion, NOW)));
        when(chatMessageRepository.findLastMessagesByChatRoomIds(List.of(otherRoomId, CHAT_ROOM_ID)))
                .thenReturn(List.of(
                        ChatMessage.of(2L, CHAT_ROOM_ID, ChatRole.AI, AI_CONTENT, NOW),
                        ChatMessage.of(4L, otherRoomId, ChatRole.USER, longQuestion, NOW)));

        // when
        List<ChatRoomResponse> result = chatService.getChatRooms(USER_ID);

        // then
        assertThat(result).extracting(ChatRoomResponse::id).containsExactly(otherRoomId, CHAT_ROOM_ID);

        ChatRoomResponse newer = result.get(0);
        assertThat(newer.title()).hasSize(50);
        assertThat(newer.modelId()).isEqualTo("study");
        assertThat(newer.lastMessage()).hasSize(100);

        ChatRoomResponse older = result.get(1);
        assertThat(older.title()).isEqualTo(CONTENT);
        assertThat(older.modelId()).isEqualTo(MODEL_ID);
        assertThat(older.lastMessage()).isEqualTo(AI_CONTENT);
    }

    @Test
    @DisplayName("채팅 목록 - 이모지가 자르는 경계에 걸려도 서로게이트 쌍이 깨지지 않는다")
    void getChatRooms_truncatesByCodePoint() {
        // given: 49자 + 이모지(UTF-16 2단위) → UTF-16 기준 50에서 자르면 이모지 절반만 남는다
        String withEmoji = "a".repeat(49) + "😀" + "tail";
        String previewWithEmoji = "a".repeat(99) + "😀" + "tail";

        when(chatRoomRepository.findByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(List.of(ChatRoom.of(CHAT_ROOM_ID, USER_ID, MODEL_ID)));
        when(chatMessageRepository.findFirstMessagesByChatRoomIds(List.of(CHAT_ROOM_ID), ChatRole.USER))
                .thenReturn(List.of(ChatMessage.of(1L, CHAT_ROOM_ID, ChatRole.USER, withEmoji, NOW)));
        when(chatMessageRepository.findLastMessagesByChatRoomIds(List.of(CHAT_ROOM_ID)))
                .thenReturn(List.of(ChatMessage.of(2L, CHAT_ROOM_ID, ChatRole.AI, previewWithEmoji, NOW)));

        // when
        ChatRoomResponse room = chatService.getChatRooms(USER_ID).get(0);

        // then
        assertThat(room.title()).isEqualTo("a".repeat(49) + "😀");
        assertThat(room.lastMessage()).isEqualTo("a".repeat(99) + "😀");
    }

    @Test
    @DisplayName("메시지 조회 - 본인 채팅방이면 메시지를 순서대로 반환한다")
    void getMessages_returnsMessages() {
        // given
        when(chatRoomRepository.findById(CHAT_ROOM_ID))
                .thenReturn(Optional.of(ChatRoom.of(CHAT_ROOM_ID, USER_ID, MODEL_ID)));
        when(chatMessageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(CHAT_ROOM_ID)).thenReturn(List.of(
                ChatMessage.of(1L, CHAT_ROOM_ID, ChatRole.USER, CONTENT, NOW),
                ChatMessage.of(2L, CHAT_ROOM_ID, ChatRole.AI, AI_CONTENT, NOW)));

        // when
        List<ChatMessageResponse> result = chatService.getMessages(USER_ID, CHAT_ROOM_ID);

        // then
        assertThat(result).extracting(ChatMessageResponse::id).containsExactly(1L, 2L);
        assertThat(result).extracting(ChatMessageResponse::role).containsExactly(ChatRole.USER, ChatRole.AI);
        assertThat(result.get(1).content()).isEqualTo(AI_CONTENT);
    }

    @Test
    @DisplayName("메시지 조회 - 존재하지 않는 채팅방이면 예외처리")
    void getMessages_roomNotExists_throws() {
        // given
        when(chatRoomRepository.findById(CHAT_ROOM_ID)).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> chatService.getMessages(USER_ID, CHAT_ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.RESOURCE_NOT_FOUND.getMessage());
        verify(chatMessageRepository, never()).findByChatRoomIdOrderByCreatedAtAscIdAsc(any());
    }

    @Test
    @DisplayName("메시지 조회 - 다른 사용자의 채팅방이면 예외처리")
    void getMessages_otherUsersRoom_throws() {
        // given
        when(chatRoomRepository.findById(CHAT_ROOM_ID))
                .thenReturn(Optional.of(ChatRoom.of(CHAT_ROOM_ID, OTHER_USER_ID, MODEL_ID)));

        // then
        assertThatThrownBy(() -> chatService.getMessages(USER_ID, CHAT_ROOM_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.RESOURCE_NOT_FOUND.getMessage());
        verify(chatMessageRepository, never()).findByChatRoomIdOrderByCreatedAtAscIdAsc(any());
    }

    @Test
    @DisplayName("채팅방이 없으면 메시지를 조회하지 않고 빈 목록을 반환한다")
    void getChatRooms_empty() {
        // given
        when(chatRoomRepository.findByUserIdOrderByCreatedAtDescIdDesc(USER_ID)).thenReturn(List.of());

        // when
        List<ChatRoomResponse> result = chatService.getChatRooms(USER_ID);

        // then
        assertThat(result).isEmpty();
        verify(chatMessageRepository, never()).findLastMessagesByChatRoomIds(any());
    }
}
