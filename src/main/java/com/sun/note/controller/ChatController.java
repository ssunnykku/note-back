package com.sun.note.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sun.note.dto.ChatMessageResponse;
import com.sun.note.dto.ChatRoomResponse;
import com.sun.note.dto.CreateChatRoomRequest;
import com.sun.note.dto.SendMessageRequest;
import com.sun.note.service.ChatService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chats")
public class ChatController {

    private final ChatService chatService;

    // 채팅 목록 조회 (최신순)
    @GetMapping
    ResponseEntity<List<ChatRoomResponse>> getChatRooms(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok().body(chatService.getChatRooms(userId));
    }

    // 채팅방 생성 (body 생략 가능)
    @PostMapping
    ResponseEntity<ChatRoomResponse> createChatRoom(
            @Valid @RequestBody(required = false) CreateChatRoomRequest dto,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        String modelId = dto == null ? null : dto.modelId();
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.createChatRoom(userId, modelId));
    }

    // 메시지 조회 (오래된 순)
    @GetMapping("{chatId}/messages")
    ResponseEntity<List<ChatMessageResponse>> getMessages(
            @PathVariable("chatId") Long chatId,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok().body(chatService.getMessages(userId, chatId));
    }

    // 메시지 전송
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
