package com.sun.note.service.it;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.sun.note.domain.entity.ChatRoom;
import com.sun.note.repository.ChatRoomRepository;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
public class ChatRoomRepositoryIT {

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    private static final UUID USER_ID = UUID.fromString("2013a306-9369-46ba-ac55-2f547ac5c50f");
    private static final UUID OTHER_USER_ID = UUID.fromString("9c4f7b3e-1234-4a5b-9c1d-abcdefabcdef");

    @BeforeEach
    void setUp() {
        chatRoomRepository.deleteAll();
    }

    @Test
    void save_generatesIncreasingIds() {
        ChatRoom first = chatRoomRepository.save(ChatRoom.of(USER_ID, "dev"));
        ChatRoom second = chatRoomRepository.save(ChatRoom.of(USER_ID, null));

        assertThat(first.getId()).isNotNull();
        assertThat(second.getId()).isGreaterThan(first.getId());
        assertThat(second.getCreatedAt()).isNotNull();
    }

    @Test
    void findByUserIdOrderByCreatedAtDescIdDesc_onlyOwnRooms_newestFirst() {
        List<ChatRoom> saved = chatRoomRepository.saveAll(List.of(
                ChatRoom.of(USER_ID, "dev"),
                ChatRoom.of(OTHER_USER_ID, "dev"),
                ChatRoom.of(USER_ID, "study")));

        List<ChatRoom> result = chatRoomRepository.findByUserIdOrderByCreatedAtDescIdDesc(USER_ID);

        assertThat(result)
                .extracting(ChatRoom::getId)
                .containsExactly(saved.get(2).getId(), saved.get(0).getId());
    }
}
