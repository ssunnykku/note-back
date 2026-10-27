package com.sun.note.service.it;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.sun.note.client.OllamaClient;
import com.sun.note.client.OllamaMessage;
import com.sun.note.domain.entity.ChatMessage;
import com.sun.note.domain.entity.ChatRole;
import com.sun.note.domain.entity.ChatRoom;
import com.sun.note.exception.BusinessException;
import com.sun.note.exception.ErrorCode;
import com.sun.note.repository.ChatMessageRepository;
import com.sun.note.repository.ChatRoomRepository;
import com.sun.note.service.ChatService;

// 실제 DB에서 sendMessage의 트랜잭션 분리 / 보상 처리 / 전송 잠금을 검증한다. (Ollama만 mock)
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
public class ChatServiceIT {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private OllamaClient ollamaClient;

    private static final UUID USER_ID = UUID.fromString("2013a306-9369-46ba-ac55-2f547ac5c50f");

    private Long roomId;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAll();
        chatRoomRepository.deleteAll();
        // 전송 시 사용자 행을 잠그므로 실제 사용자 행이 있어야 한다.
        jdbcTemplate.update("""
                insert into users (id, name, email, password, created_at) values (?, ?, ?, ?, now())
                on conflict (id) do nothing
                """, USER_ID, "it-user", "chat-it@test.com", "x");
        roomId = chatRoomRepository.save(ChatRoom.of(USER_ID, "dev")).getId();
        when(ollamaClient.getMaxCallDuration()).thenReturn(Duration.ofSeconds(125));
    }

    // Ollama 호출 도중 내 잠금이 만료되고 다른 요청이 새 잠금을 잡은 상황을 만든다.
    private LocalDateTime takeOverLockByAnotherRequest() {
        LocalDateTime othersLock = LocalDateTime.of(2099, 1, 1, 0, 0);
        jdbcTemplate.update("update chat_rooms set pending_until = ? where id = ?", othersLock, roomId);
        return othersLock;
    }

    private ChatRoom reloadRoom() {
        return chatRoomRepository.findById(roomId).orElseThrow();
    }

    private List<ChatMessage> messages() {
        return chatMessageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(roomId);
    }

    @Test
    void success_persistsMessages_updatesModel_releasesLock() {
        when(ollamaClient.chat(eq("study"), any())).thenReturn("답변");

        chatService.sendMessage(USER_ID, roomId, "질문", "study");

        assertThat(messages()).extracting(ChatMessage::getRole, ChatMessage::getContent)
                .containsExactly(
                        tuple(ChatRole.USER, "질문"),
                        tuple(ChatRole.AI, "답변"));
        assertThat(reloadRoom().getModelId()).isEqualTo("study");
        assertThat(reloadRoom().getPendingUntil()).isNull();
    }

    @Test
    void ollamaCall_runsOutsideTransaction_withNoConnectionBound() {
        AtomicBoolean txActive = new AtomicBoolean(true);
        AtomicBoolean resourcesBound = new AtomicBoolean(true);
        when(ollamaClient.chat(anyString(), any())).thenAnswer(invocation -> {
            txActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            resourcesBound.set(!TransactionSynchronizationManager.getResourceMap().isEmpty());
            return "답변";
        });

        chatService.sendMessage(USER_ID, roomId, "질문", "dev");

        assertThat(txActive).isFalse();
        assertThat(resourcesBound).as("EntityManager/커넥션이 바인딩되어 있으면 안 된다").isFalse();
    }

    @Test
    void ollamaFails_nothingPersisted_modelUnchanged_lockReleased() {
        when(ollamaClient.chat(anyString(), any())).thenThrow(new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> chatService.sendMessage(USER_ID, roomId, "질문", "study"))
                .hasMessage(ErrorCode.AI_SERVICE_UNAVAILABLE.getMessage());

        assertThat(messages()).isEmpty();
        assertThat(reloadRoom().getModelId()).isEqualTo("dev");
        assertThat(reloadRoom().getPendingUntil()).isNull();
    }

    @Test
    void saveAiFails_userMessageRemoved_lockReleased() {
        // content가 null이면 ChatMessage NOT NULL 검증으로 AI 메시지 저장(tx2)이 실패한다.
        when(ollamaClient.chat(anyString(), any())).thenReturn(null);

        assertThatThrownBy(() -> chatService.sendMessage(USER_ID, roomId, "질문", "study"));

        assertThat(messages()).isEmpty();
        assertThat(reloadRoom().getModelId()).isEqualTo("dev");
        assertThat(reloadRoom().getPendingUntil()).isNull();
    }

    @Test
    void failedMessage_isNotSentInNextHistory() {
        when(ollamaClient.chat(eq("missing"), any())).thenThrow(new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE));
        when(ollamaClient.chat(eq("dev"), any())).thenAnswer(invocation -> {
            List<OllamaMessage> history = invocation.getArgument(1);
            return "받은 메시지 " + history.size() + "개";
        });

        assertThatThrownBy(() -> chatService.sendMessage(USER_ID, roomId, "실패할 질문", "missing"));
        var response = chatService.sendMessage(USER_ID, roomId, "다음 질문", "dev");

        assertThat(response.content()).isEqualTo("받은 메시지 1개");
    }

    @Test
    void concurrentSendToSameRoom_secondIsRejectedWith409() throws Exception {
        CountDownLatch firstCallEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstCall = new CountDownLatch(1);
        when(ollamaClient.chat(anyString(), any())).thenAnswer(invocation -> {
            firstCallEntered.countDown();
            releaseFirstCall.await(10, TimeUnit.SECONDS);
            return "답변";
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = executor.submit(() -> chatService.sendMessage(USER_ID, roomId, "첫 질문", "dev"));
            assertThat(firstCallEntered.await(10, TimeUnit.SECONDS)).isTrue();

            // 첫 요청이 AI 응답을 기다리는 동안 같은 방에 전송 → 409
            assertThatThrownBy(() -> chatService.sendMessage(USER_ID, roomId, "끼어든 질문", "dev"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.CHAT_IN_PROGRESS.getMessage());

            releaseFirstCall.countDown();
            first.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(messages()).extracting(ChatMessage::getContent).containsExactly("첫 질문", "답변");
        assertThat(reloadRoom().getPendingUntil()).isNull();
    }

    @Test
    void expiredLock_allowsSend() {
        // 서버가 응답 도중 죽어 잠금이 풀리지 않은 상황
        jdbcTemplate.update("update chat_rooms set pending_until = ? where id = ?",
                LocalDateTime.now().minusMinutes(1), roomId);
        when(ollamaClient.chat(anyString(), any())).thenReturn("답변");

        chatService.sendMessage(USER_ID, roomId, "질문", "dev");

        assertThat(messages()).hasSize(2);
    }

    @Test
    void failureAfterLockTakenOver_doesNotReleaseOthersLock() {
        when(ollamaClient.chat(anyString(), any())).thenAnswer(invocation -> {
            takeOverLockByAnotherRequest();
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        });

        assertThatThrownBy(() -> chatService.sendMessage(USER_ID, roomId, "질문", "dev"));

        assertThat(messages()).isEmpty();
        assertThat(reloadRoom().getPendingUntil()).isEqualTo(LocalDateTime.of(2099, 1, 1, 0, 0));
    }

    @Test
    void successAfterLockTakenOver_doesNotReleaseOthersLock_orChangeModel() {
        when(ollamaClient.chat(anyString(), any())).thenAnswer(invocation -> {
            takeOverLockByAnotherRequest();
            return "답변";
        });

        chatService.sendMessage(USER_ID, roomId, "질문", "study");

        assertThat(reloadRoom().getPendingUntil()).isEqualTo(LocalDateTime.of(2099, 1, 1, 0, 0));
        assertThat(reloadRoom().getModelId()).isEqualTo("dev");
    }

    @Test
    void sendToOtherRoomWhileWaiting_isRejectedWith409() throws Exception {
        Long otherRoomId = chatRoomRepository.save(ChatRoom.of(USER_ID, "dev")).getId();
        CountDownLatch firstCallEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstCall = new CountDownLatch(1);
        when(ollamaClient.chat(anyString(), any())).thenAnswer(invocation -> {
            firstCallEntered.countDown();
            releaseFirstCall.await(10, TimeUnit.SECONDS);
            return "답변";
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = executor.submit(() -> chatService.sendMessage(USER_ID, roomId, "첫 질문", "dev"));
            assertThat(firstCallEntered.await(10, TimeUnit.SECONDS)).isTrue();

            // 사용자당 동시 전송은 1건: 다른 방이어도 409
            assertThatThrownBy(() -> chatService.sendMessage(USER_ID, otherRoomId, "다른 방 질문", "dev"))
                    .hasMessage(ErrorCode.CHAT_IN_PROGRESS.getMessage());

            releaseFirstCall.countDown();
            first.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // 첫 전송이 끝나면 다른 방에도 보낼 수 있다.
        chatService.sendMessage(USER_ID, otherRoomId, "다른 방 질문", "dev");
        assertThat(chatMessageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(otherRoomId)).hasSize(2);
    }

    @Test
    void simultaneousSendsToDifferentRooms_onlyOneSucceeds() throws Exception {
        Long otherRoomId = chatRoomRepository.save(ChatRoom.of(USER_ID, "dev")).getId();
        when(ollamaClient.chat(anyString(), any())).thenAnswer(invocation -> {
            Thread.sleep(500);
            return "답변";
        });

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = List.of(roomId, otherRoomId).stream()
                    .map(id -> executor.submit(() -> {
                        start.await();
                        try {
                            chatService.sendMessage(USER_ID, id, "질문", "dev");
                            return true;
                        } catch (BusinessException e) {
                            assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CHAT_IN_PROGRESS);
                            return false;
                        }
                    }))
                    .toList();
            start.countDown();

            int succeeded = 0;
            for (Future<Boolean> result : results) {
                succeeded += result.get(10, TimeUnit.SECONDS) ? 1 : 0;
            }
            assertThat(succeeded).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rateLimitExceeded_rejectsWith429_andReleasesLock() {
        // 최근 2분 안에 보낸 사용자 메시지가 이미 10건 (기본 제한: 2분 10회)
        for (int i = 0; i < 10; i++) {
            chatMessageRepository.save(ChatMessage.of(roomId, ChatRole.USER, "이전 질문 " + i));
        }

        assertThatThrownBy(() -> chatService.sendMessage(USER_ID, roomId, "질문", "dev"))
                .hasMessage(ErrorCode.CHAT_RATE_LIMITED.getMessage());
        assertThat(messages()).hasSize(10);
        assertThat(reloadRoom().getPendingUntil()).as("tx1 롤백으로 전송 잠금도 취소").isNull();

        // 2분이 지난 메시지는 세지 않는다.
        jdbcTemplate.update("update chat_messages set created_at = ? where chat_room_id = ?",
                LocalDateTime.now().minusMinutes(3), roomId);
        when(ollamaClient.chat(anyString(), any())).thenReturn("답변");

        chatService.sendMessage(USER_ID, roomId, "질문", "dev");
        assertThat(messages()).hasSize(12);
    }

    @Test
    void activeLock_rejectsSend() {
        jdbcTemplate.update("update chat_rooms set pending_until = ? where id = ?",
                LocalDateTime.now().plusMinutes(1), roomId);

        assertThatThrownBy(() -> chatService.sendMessage(USER_ID, roomId, "질문", "dev"))
                .hasMessage(ErrorCode.CHAT_IN_PROGRESS.getMessage());
        assertThat(messages()).isEmpty();
    }
}
