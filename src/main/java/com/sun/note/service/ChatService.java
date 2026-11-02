package com.sun.note.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int TITLE_MAX_LENGTH = 50;
    private static final int PREVIEW_MAX_LENGTH = 100;

    // 정상 요청의 잠금이 Ollama 응답 전에 풀리지 않도록 timeout에 더하는 여유분 (DB 커넥션 대기 등)
    private static final Duration SEND_LOCK_MARGIN = Duration.ofSeconds(60);

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final OllamaClient ollamaClient;
    private final TransactionTemplate transactionTemplate;
    private final ChatProperties chatProperties;

    @Transactional
    public ChatRoomResponse createChatRoom(UUID userId, String modelId) {
        ChatRoom chatRoom = chatRoomRepository.save(ChatRoom.of(userId, normalizeModelId(modelId)));
        return ChatRoomResponse.of(chatRoom.getId(), null, chatRoom.getModelId(), null, chatRoom.getCreatedAt());
    }

    // Ollama 응답을 기다리는 동안 DB 커넥션을 잡지 않도록 트랜잭션을 둘로 나눈다.
    // 사용자당 동시 전송은 1건: 같은 방이면 답 없는 메시지가 히스토리에 섞이고, 다른 방이면 Ollama를 독점한다.
    public ChatMessageResponse sendMessage(UUID userId, Long chatRoomId, String content, String rawModelId) {
        String modelId = rawModelId.strip();
        PreparedMessage prepared = Objects.requireNonNull(transactionTemplate.execute(status -> {
            // 다른 방 동시 전송이 둘 다 잠금을 얻거나 횟수 제한을 함께 통과하지 않도록 사용자 단위로 직렬화
            userRepository.lockForChat(userId);

            LocalDateTime now = LocalDateTime.now();
            // DB가 마이크로초까지만 저장하므로, 해제 시 값 비교가 맞도록 잘라 둔다
            LocalDateTime lockUntil = now.plus(sendLockTtl()).truncatedTo(ChronoUnit.MICROS);
            if (chatRoomRepository.acquireSendLock(chatRoomId, userId, now, lockUntil) == 0) {
                findOwnedChatRoom(userId, chatRoomId);
                throw new BusinessException(ErrorCode.CHAT_IN_PROGRESS);
            }

            // 여기서 예외가 나면 tx1 롤백으로 위의 잠금도 취소된다
            ChatProperties.RateLimit rateLimit = chatProperties.rateLimit();
            if (chatMessageRepository.countByUserIdAndRoleSince(userId, ChatRole.USER,
                    now.minus(rateLimit.window())) >= rateLimit.maxRequests()) {
                throw new BusinessException(ErrorCode.CHAT_RATE_LIMITED);
            }

            List<OllamaMessage> ollamaMessages = buildOllamaMessages(chatRoomId, content);

            ChatMessage userMessage = chatMessageRepository.save(ChatMessage.of(chatRoomId, ChatRole.USER, content));
            return new PreparedMessage(userMessage.getId(), lockUntil, ollamaMessages);
        }));

        try {
            String aiContent = ollamaClient.chat(modelId, prepared.ollamaMessages());

            ChatMessage aiMessage = Objects.requireNonNull(transactionTemplate.execute(status -> {
                if (chatRoomRepository.completeSend(chatRoomId, prepared.lockUntil(), modelId, LocalDateTime.now()) == 0) {
                    // 잠금이 만료돼 다른 요청이 가져갔다. lockUntil이 다르므로 그 잠금은 건드리지 않는다
                    log.warn("전송 잠금이 만료된 뒤 응답 완료: chatRoomId={}", chatRoomId);
                }
                return chatMessageRepository.save(ChatMessage.of(chatRoomId, ChatRole.AI, aiContent));
            }));
            return ChatMessageResponse.from(aiMessage);
        } catch (Throwable e) {
            // 실패하면 아무것도 남기지 않는다. Error(OOM 등)도 포함
            rollbackSend(chatRoomId, prepared, e);
            throw e;
        }
    }

    // 보상이 실패해도 원래 예외를 전달한다. 잠금은 lockUntil이 지나면 자동으로 풀린다
    private void rollbackSend(Long chatRoomId, PreparedMessage prepared, Throwable cause) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                chatMessageRepository.deleteById(prepared.userMessageId());
                chatRoomRepository.releaseSendLock(chatRoomId, prepared.lockUntil());
            });
        } catch (RuntimeException rollbackFailure) {
            log.error("메시지 전송 실패 후 보상 처리 실패: chatRoomId={}, messageId={}", chatRoomId,
                    prepared.userMessageId(), rollbackFailure);
            cause.addSuppressed(rollbackFailure);
        }
    }

    // 모델 컨텍스트를 넘지 않도록 최근 대화만 보낸다. 대화가 AI 답변으로 시작하지 않게 맨 앞 AI 메시지는 뺀다
    private List<OllamaMessage> buildOllamaMessages(Long chatRoomId, String content) {
        ChatProperties.History limit = chatProperties.history();
        List<ChatMessage> recent = chatMessageRepository.findByChatRoomIdOrderByCreatedAtDescIdDesc(
                chatRoomId, PageRequest.of(0, limit.maxMessages()));

        Deque<ChatMessage> window = new ArrayDeque<>();
        int remainingChars = limit.maxChars() - content.length();
        for (ChatMessage message : recent) {
            remainingChars -= message.getContent().length();
            if (remainingChars < 0) {
                break;
            }
            window.addFirst(message);
        }
        while (!window.isEmpty() && window.peekFirst().getRole() == ChatRole.AI) {
            window.pollFirst();
        }

        List<OllamaMessage> ollamaMessages = window.stream()
                .map(m -> new OllamaMessage(toOllamaRole(m.getRole()), m.getContent()))
                .collect(Collectors.toCollection(ArrayList::new));
        ollamaMessages.add(new OllamaMessage(toOllamaRole(ChatRole.USER), content));
        return ollamaMessages;
    }

    private String normalizeModelId(String modelId) {
        return StringUtils.hasText(modelId) ? modelId.strip() : null;
    }

    private Duration sendLockTtl() {
        return ollamaClient.getMaxCallDuration().plus(SEND_LOCK_MARGIN);
    }

    private record PreparedMessage(Long userMessageId, LocalDateTime lockUntil, List<OllamaMessage> ollamaMessages) {
    }

    public List<ChatMessageResponse> getMessages(UUID userId, Long chatRoomId) {
        findOwnedChatRoom(userId, chatRoomId);
        return chatMessageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(chatRoomId).stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    // 제목은 별도 컬럼 없이 첫 사용자 메시지로 채운다
    public List<ChatRoomResponse> getChatRooms(UUID userId) {
        List<ChatRoom> chatRooms = chatRoomRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId);
        if (chatRooms.isEmpty()) {
            return List.of();
        }

        List<Long> chatRoomIds = chatRooms.stream().map(ChatRoom::getId).toList();
        Map<Long, String> firstUserMessages = toContentMap(
                chatMessageRepository.findFirstMessagesByChatRoomIds(chatRoomIds, ChatRole.USER));
        Map<Long, String> lastMessages = toContentMap(
                chatMessageRepository.findLastMessagesByChatRoomIds(chatRoomIds));

        return chatRooms.stream()
                .map(room -> ChatRoomResponse.of(
                        room.getId(),
                        truncate(firstUserMessages.get(room.getId()), TITLE_MAX_LENGTH),
                        room.getModelId(),
                        truncate(lastMessages.get(room.getId()), PREVIEW_MAX_LENGTH),
                        room.getCreatedAt()))
                .toList();
    }

    private Map<Long, String> toContentMap(List<ChatMessage> messages) {
        return messages.stream()
                .collect(Collectors.toMap(ChatMessage::getChatRoomId, ChatMessage::getContent));
    }

    // UTF-16 단위로 자르면 이모지(서로게이트 쌍)가 깨지므로 코드포인트 단위로 자른다
    private String truncate(String text, int maxLength) {
        if (text == null || text.codePointCount(0, text.length()) <= maxLength) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, maxLength));
    }

    // 다른 사용자의 채팅방은 존재 여부를 드러내지 않도록 없는 방과 같이 404
    private ChatRoom findOwnedChatRoom(UUID userId, Long chatRoomId) {
        return chatRoomRepository.findById(chatRoomId)
                .filter(room -> room.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private String toOllamaRole(ChatRole role) {
        return role == ChatRole.AI ? "assistant" : "user";
    }
}
