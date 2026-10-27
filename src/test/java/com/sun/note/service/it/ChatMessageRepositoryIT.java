package com.sun.note.service.it;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.sun.note.domain.entity.ChatMessage;
import com.sun.note.domain.entity.ChatRole;
import com.sun.note.repository.ChatMessageRepository;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
public class ChatMessageRepositoryIT {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    private static final Long ROOM_A = 1700000000000L;
    private static final Long ROOM_B = 1700000000001L;
    private static final Long ROOM_OTHER = 1700000000002L;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAll();
        chatMessageRepository.saveAll(List.of(
                ChatMessage.of(ROOM_A, ChatRole.USER, "A 첫 질문"),
                ChatMessage.of(ROOM_A, ChatRole.AI, "A 첫 답변"),
                ChatMessage.of(ROOM_A, ChatRole.USER, "A 두번째 질문"),
                ChatMessage.of(ROOM_A, ChatRole.AI, "A 마지막 답변"),
                ChatMessage.of(ROOM_B, ChatRole.USER, "B 질문"),
                ChatMessage.of(ROOM_OTHER, ChatRole.USER, "조회 대상 아님")));
    }

    @Test
    void findByChatRoomIdOrderByCreatedAtAscIdAsc_keepsInsertOrder() {
        List<ChatMessage> result = chatMessageRepository.findByChatRoomIdOrderByCreatedAtAscIdAsc(ROOM_A);

        assertThat(result)
                .extracting(ChatMessage::getContent)
                .containsExactly("A 첫 질문", "A 첫 답변", "A 두번째 질문", "A 마지막 답변");
    }

    @Test
    void findLastMessagesByChatRoomIds() {
        List<ChatMessage> result = chatMessageRepository.findLastMessagesByChatRoomIds(List.of(ROOM_A, ROOM_B));

        assertThat(result)
                .extracting(ChatMessage::getChatRoomId, ChatMessage::getContent)
                .containsExactlyInAnyOrder(
                        tuple(ROOM_A, "A 마지막 답변"),
                        tuple(ROOM_B, "B 질문"));
    }

    @Test
    void findFirstMessagesByChatRoomIds() {
        List<ChatMessage> result = chatMessageRepository.findFirstMessagesByChatRoomIds(
                List.of(ROOM_A, ROOM_B), ChatRole.USER);

        assertThat(result)
                .extracting(ChatMessage::getChatRoomId, ChatMessage::getContent)
                .containsExactlyInAnyOrder(
                        tuple(ROOM_A, "A 첫 질문"),
                        tuple(ROOM_B, "B 질문"));
    }
}
