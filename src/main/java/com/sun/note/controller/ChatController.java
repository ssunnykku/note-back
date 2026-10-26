package com.sun.note.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sun.note.dto.ChatMessageResponse;
import com.sun.note.dto.SendMessageRequest;
import com.sun.note.service.ChatService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chats")
public class ChatController {

    private final ChatService chatService;

    // 메시지 전송 (채팅방이 없으면 생성 후 전송)
    @PostMapping("{chatId}/messages")
    ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable("chatId") Long chatId,
            @Valid @RequestBody SendMessageRequest dto,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        ChatMessageResponse response = chatService.sendMessage(userId, chatId, dto.content(), dto.modelId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
