package com.sun.note.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

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

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final OllamaClient ollamaClient;

    @Transactional
    public ChatMessageResponse sendMessage(UUID userId, Long chatRoomId, String content, String modelId) {
        ChatRoom chatRoom = findOrCreateChatRoom(userId, chatRoomId, modelId);
        chatRoom.updateModel(modelId);

        List<ChatMessage> history = chatMessageRepository.findByChatRoomIdOrderByCreatedAtAsc(chatRoomId);
        List<OllamaMessage> ollamaMessages = history.stream()
                .map(m -> new OllamaMessage(toOllamaRole(m.getRole()), m.getContent()))
                .collect(Collectors.toCollection(ArrayList::new));
        ollamaMessages.add(new OllamaMessage(toOllamaRole(ChatRole.USER), content));

        chatMessageRepository.save(ChatMessage.of(chatRoomId, ChatRole.USER, content));

        String aiContent = ollamaClient.chat(modelId, ollamaMessages);

        ChatMessage aiMessage = chatMessageRepository.save(ChatMessage.of(chatRoomId, ChatRole.AI, aiContent));

        return ChatMessageResponse.of(aiMessage.getId(), aiMessage.getContent(), aiMessage.getCreatedAt());
    }

    private ChatRoom findOrCreateChatRoom(UUID userId, Long chatRoomId, String modelId) {
        return chatRoomRepository.findById(chatRoomId)
                .map(room -> {
                    if (!room.getUserId().equals(userId)) {
                        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
                    }
                    return room;
                })
                .orElseGet(() -> chatRoomRepository.save(ChatRoom.of(chatRoomId, userId, modelId)));
    }

    private String toOllamaRole(ChatRole role) {
        return role == ChatRole.AI ? "assistant" : "user";
    }
}
