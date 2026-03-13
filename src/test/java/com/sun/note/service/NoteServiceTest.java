package com.sun.note.service;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.note.domain.entity.Note;
import com.sun.note.dto.NoteResponse;
import com.sun.note.exception.BusinessException;
import com.sun.note.exception.ErrorCode;
import com.sun.note.repository.NoteRepository;

@ExtendWith(MockitoExtension.class)
public class NoteServiceTest {

    @Mock
    private NoteRepository noteRepository;

    @InjectMocks
    private NoteService noteService;

    private static final UUID USER_ID = UUID.fromString("2013a306-9369-46ba-ac55-2f547ac5c50f");
    private static final Long CATEGORY_ID = 1L;
    private static final String TITLE = "첫번째 노트";
    private static final String CONTENT = "#내용";

    private Note createNote() {
        return Note.of(1L, USER_ID, CATEGORY_ID, TITLE, CONTENT);
    }

    @Test
    @DisplayName("노트 생성")
    void testAddNote() {
        // given
        Note noteResult = createNote();

        when(noteRepository.save(any(Note.class))).thenReturn(noteResult);

        // when
        NoteResponse result = noteService.addNote(USER_ID, CATEGORY_ID, TITLE, CONTENT);

        // then
        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(UUID.fromString("2013a306-9369-46ba-ac55-2f547ac5c50f"));
        assertThat(result.categoryId()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("첫번째 노트");
        assertThat(result.content()).isEqualTo("#내용");

    }

    @Test
    @DisplayName("노트 수정")
    void testEditNote() {
        // given
        Note note = createNote();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        // when
        NoteResponse result = noteService.editNote(note.getId(), 2L, "첫번째 노트 수정", "#내용 수정");

        // then
        assertThat(result).isNotNull();
        assertThat(result.categoryId()).isEqualTo(2L);
        assertThat(result.title()).isEqualTo("첫번째 노트 수정");
        assertThat(result.content()).isEqualTo("#내용 수정");

    }

    @Test
    @DisplayName("데이터가 없으면 예외처리(수정)")
    void testEditNoteException() {
        when(noteRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.editNote(1L, CATEGORY_ID, TITLE, CONTENT))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.RESOURCE_NOT_FOUND.getMessage());

    }

    @Test
    @DisplayName("노트 데이터가 없으면 예외처리(조회)")
    void testFindByIdException() {
        when(noteRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.getById(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.RESOURCE_NOT_FOUND.getMessage());

    }

}
