package com.sun.note.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
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

    // 채팅 목록에 내려줄 제목/미리보기 최대 길이
    private static final int TITLE_MAX_LENGTH = 50;
    private static final int PREVIEW_MAX_LENGTH = 100;

    // 전송 잠금 유지 시간 = Ollama 최대 호출 시간(connect + read timeout) + 여유분.
    // 정상 진행 중인 요청의 잠금이 먼저 만료되어 다른 요청이 끼어드는 일이 없도록 timeout 설정에서 계산한다.
    // (여유분: tx1/tx2의 커넥션 대기(Hikari 기본 30초) 등)
    private static final Duration SEND_LOCK_MARGIN = Duration.ofSeconds(60);

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final OllamaClient ollamaClient;
    private final TransactionTemplate transactionTemplate;
    private final ChatProperties chatProperties;

    // 채팅방 생성 (id는 서버에서 발급)
    @Transactional
    public ChatRoomResponse createChatRoom(UUID userId, String modelId) {
        // 앞뒤 공백은 지우고, 비어 있으면 "모델 미지정"(null)으로 취급한다.
        ChatRoom chatRoom = chatRoomRepository.save(ChatRoom.of(userId, normalizeModelId(modelId)));
        return ChatRoomResponse.of(chatRoom.getId(), null, chatRoom.getModelId(), null, chatRoom.getCreatedAt());
    }

    // Ollama 응답(최대 수십 초)을 기다리는 동안 DB 커넥션을 점유하지 않도록 트랜잭션을 나눈다.
    //   tx1: 사용자 행 잠금 + 전송 잠금 획득(소유자 확인 포함) + 횟수 제한 확인 + 히스토리 조회 + 사용자 메시지 저장
    //   (트랜잭션 밖) Ollama 호출
    //   tx2: AI 메시지 저장 + 모델 갱신 + 잠금 해제
    // Ollama 호출이나 tx2가 실패하면 사용자 메시지를 삭제하고 잠금을 풀어 "실패하면 아무것도 저장되지 않는다"를 유지한다.
    // 사용자의 채팅방 중 응답 대기 중인 전송이 있으면 409로 거절한다. (같은 방에서 허용하면 아직 답이 없는
    // 다른 요청의 사용자 메시지가 히스토리에 섞이고, 다른 방까지 허용하면 한 사용자가 Ollama를 독점한다)
    // 전송 횟수 제한(chat.rate-limit)을 넘으면 429로 거절한다.
    // 잠금 만료 시각(lockUntil)을 소유 토큰으로 써서, 만료 후 다른 요청이 잡은 잠금은 해제하지 않는다.
    public ChatMessageResponse sendMessage(UUID userId, Long chatRoomId, String content, String rawModelId) {
        // modelId는 그대로 Ollama 모델명이 되므로 앞뒤 공백을 지운다. (요청 검증에서 공백만 있는 값은 이미 400)
        String modelId = rawModelId.strip();
        PreparedMessage prepared = transactionTemplate.execute(status -> {
            // 같은 사용자의 전송을 직렬화한다. 다른 방 동시 전송이 둘 다 잠금을 얻거나 횟수 제한을 함께 통과하는 것을 막는다.
            userRepository.lockForChat(userId);

            LocalDateTime now = LocalDateTime.now();
            // DB(timestamp)는 마이크로초까지 저장하므로 맞춰 잘라야 해제 시 값 비교가 일치한다.
            LocalDateTime lockUntil = now.plus(sendLockTtl()).truncatedTo(ChronoUnit.MICROS);
            if (chatRoomRepository.acquireSendLock(chatRoomId, userId, now, lockUntil) == 0) {
                findOwnedChatRoom(userId, chatRoomId);
                throw new BusinessException(ErrorCode.CHAT_IN_PROGRESS);
            }

            // 예외로 끝나면 tx1이 롤백되어 위에서 잡은 전송 잠금도 함께 취소된다.
            ChatProperties.RateLimit rateLimit = chatProperties.rateLimit();
            if (chatMessageRepository.countByUserIdAndRoleSince(userId, ChatRole.USER,
                    now.minus(rateLimit.window())) >= rateLimit.maxRequests()) {
                throw new BusinessException(ErrorCode.CHAT_RATE_LIMITED);
            }

            List<OllamaMessage> ollamaMessages = buildOllamaMessages(chatRoomId, content);

            ChatMessage userMessage = chatMessageRepository.save(ChatMessage.of(chatRoomId, ChatRole.USER, content));
            return new PreparedMessage(userMessage.getId(), lockUntil, ollamaMessages);
        });

        try {
            String aiContent = ollamaClient.chat(modelId, prepared.ollamaMessages());

            ChatMessage aiMessage = transactionTemplate.execute(status -> {
                if (chatRoomRepository.completeSend(chatRoomId, prepared.lockUntil(), modelId, LocalDateTime.now()) == 0) {
                    // 잠금이 만료되어 다른 요청이 가져간 경우. 그 요청의 잠금은 건드리지 않는다.
                    log.warn("전송 잠금이 만료된 뒤 응답 완료: chatRoomId={}", chatRoomId);
                }
                return chatMessageRepository.save(ChatMessage.of(chatRoomId, ChatRole.AI, aiContent));
            });
            return ChatMessageResponse.from(aiMessage);
        } catch (Throwable e) {
            // Error(OOM 등)에서도 사용자 메시지가 남지 않도록 Throwable 전체에 대해 보상한다.
            rollbackSend(chatRoomId, prepared, e);
            throw e;
        }
    }

    // 보상 처리: 사용자 메시지 삭제 + 잠금 해제.
    // 보상이 실패해도 원래 예외를 클라이언트에 전달한다. (잠금은 SEND_LOCK_TTL 후 자동 만료)
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

    // 최근 메시지부터 거슬러 올라가며 개수(maxMessages)와 글자 수(maxChars, 새 메시지 포함) 안에 드는 만큼만
    // 이전 대화로 보낸다. 대화가 AI 답변으로 시작하지 않도록 맨 앞의 AI 메시지는 뺀다.
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

    // 메시지 조회 (오래된 순)
    public List<ChatMessageResponse> getMessages(UUID userId, Long chatRoomId) {
        findOwnedChatRoom(userId, chatRoomId);
        return chatMessageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(chatRoomId).stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    // 채팅 목록 조회 (최신순)
    // 제목은 별도 컬럼 없이 첫 사용자 메시지로, lastMessage는 가장 최근 메시지로 채운다.
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

    // 코드포인트 단위로 자른다. (UTF-16 단위로 자르면 이모지 서로게이트 쌍이 깨진다)
    private String truncate(String text, int maxLength) {
        if (text == null || text.codePointCount(0, text.length()) <= maxLength) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, maxLength));
    }

    // 없는 채팅방과 다른 사용자의 채팅방을 구분하지 않고 모두 404로 응답한다.
    private ChatRoom findOwnedChatRoom(UUID userId, Long chatRoomId) {
        return chatRoomRepository.findById(chatRoomId)
                .filter(room -> room.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private String toOllamaRole(ChatRole role) {
        return role == ChatRole.AI ? "assistant" : "user";
    }
}
