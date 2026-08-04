package com.sun.note.service.it;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;

import com.sun.note.repository.NoteRepository;
import com.sun.note.domain.entity.Note;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
public class NoteServiceIT {

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private EntityManagerFactory emf;

    private static final UUID USER_ID = UUID.fromString("2013a306-9369-46ba-ac55-2f547ac5c50f");
    private static final Long CATEGORY_ID = 1L;
    private static final String TITLE = "첫번째 노트";
    private static final String CONTENT = "#내용";

    @BeforeEach
    void setUp() {
        noteRepository.deleteAll();
        noteRepository.save(Note.of(USER_ID, CATEGORY_ID, TITLE, CONTENT));
    }

    @Test
    void testOptimisticLocking() {
        Long noteId = noteRepository.findAll().get(0).getId();

        // 별도의 EntityManager 2개로 같은 row를 각각 조회
        EntityManager em1 = emf.createEntityManager();
        EntityManager em2 = emf.createEntityManager();

        Note note1 = em1.find(Note.class, noteId);
        Note note2 = em2.find(Note.class, noteId);

        // em1에서 먼저 수정 → version 증가
        em1.getTransaction().begin();
        note1.edit(CATEGORY_ID, TITLE, "1234");
        em1.flush();
        em1.getTransaction().commit();
        em1.close();

        // em2에서 수정 시도 → stale version → OptimisticLockException
        em2.getTransaction().begin();
        note2.edit(CATEGORY_ID, TITLE, "ㄱㄴㄷㄹ");
        assertThrows(OptimisticLockException.class, () -> {
            em2.flush();
        });
        em2.getTransaction().rollback();
        em2.close();
    }

}
