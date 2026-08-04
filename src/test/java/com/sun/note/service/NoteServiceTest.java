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

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
    @DisplayName("노트 생성(카테고리 미분류)")
    void testAddNoteWithoutCategory() {
        // given
        Note noteResult = Note.of(1L, USER_ID, null, TITLE, CONTENT);

        when(noteRepository.save(any(Note.class))).thenReturn(noteResult);

        // when
        NoteResponse result = noteService.addNote(USER_ID, null, TITLE, CONTENT);

        // then
        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(UUID.fromString("2013a306-9369-46ba-ac55-2f547ac5c50f"));
        assertThat(result.categoryId()).isEqualTo(null);
        assertThat(result.title()).isEqualTo("첫번째 노트");
        assertThat(result.content()).isEqualTo("#내용");

    }

    @Test
    @DisplayName("노트 수정")
    void testEditNote() {
        // given
        Note note = createNote();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));
        when(noteRepository.saveAndFlush(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        NoteResponse result = noteService.editNote(note.getId(), CATEGORY_ID, "첫번째 노트 수정", "#내용 수정", note.getVersion());

        // then
        assertThat(result).isNotNull();
        assertThat(result.categoryId()).isEqualTo(CATEGORY_ID);
        assertThat(result.title()).isEqualTo("첫번째 노트 수정");
        assertThat(result.content()).isEqualTo("#내용 수정");
    }

    @Test
    @DisplayName("노트 수정 시 응답에 변경된 버전이 포함되어야 한다")
    void testEditNoteReturnsUpdatedVersion() throws Exception {
        // given
        Note note = createNote(); // version = 0L
        Long originalVersion = note.getVersion();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));
        when(noteRepository.saveAndFlush(any(Note.class))).thenAnswer(invocation -> {
            Note saved = invocation.getArgument(0);
            // JPA @Version flush 시뮬레이션: version + 1
            Field versionField = Note.class.getDeclaredField("version");
            versionField.setAccessible(true);
            versionField.set(saved, saved.getVersion() + 1);
            return saved;
        });

        // when
        NoteResponse result = noteService.editNote(note.getId(), CATEGORY_ID, "수정", "#수정", originalVersion);

        // then - 수정 후 응답 version은 요청 version보다 커야 한다
        assertThat(result.version()).isEqualTo(originalVersion + 1);
    }

    @Test
    @DisplayName("데이터가 없으면 예외처리(수정)")
    void testEditNoteException() {
        when(noteRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.editNote(1L, CATEGORY_ID, TITLE, CONTENT, 0L))
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

    @Test
    @DisplayName("삭제된 노트 조회 시 예외처리")
    void testGetDeletedNoteException() {
        // given
        Note note = createNote();
        note.softDelete();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        // then
        assertThatThrownBy(() -> noteService.getById(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.RESOURCE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("버전 불일치 시 예외처리(수정)")
    void testEditNoteVersionConflict() {
        // given
        Note note = createNote();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        // then
        Long wrongVersion = 999L;
        assertThatThrownBy(() -> noteService.editNote(1L, CATEGORY_ID, TITLE, CONTENT, wrongVersion))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.VERSION_CONFLICT.getMessage());
    }

    @Test
    @DisplayName("삭제된 노트 수정 시 예외처리")
    void testEditDeletedNoteException() {
        // given
        Note note = createNote();
        note.softDelete();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        // then
        assertThatThrownBy(() -> noteService.editNote(1L, CATEGORY_ID, TITLE, CONTENT, 0L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.RESOURCE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("노트 soft delete")
    void testSoftDelete() {
        // given
        Note note = createNote();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        // when
        noteService.softDelete(1L);

        // then
        assertThat(note.getDeleted()).isTrue();
        assertThat(note.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 삭제된 노트 soft delete 시 예외처리")
    void testSoftDeleteAlreadyDeleted() {
        // given
        Note note = createNote();
        note.softDelete();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        // then
        assertThatThrownBy(() -> noteService.softDelete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.ALREADY_DELETED.getMessage());
    }

    @Test
    @DisplayName("노트 데이터가 없으면 예외처리(soft delete)")
    void testSoftDeleteNotFound() {
        when(noteRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.softDelete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.RESOURCE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("노트 복원")
    void testRestore() {
        // given
        Note note = createNote();
        note.softDelete();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        // when
        noteService.restore(1L);

        // then
        assertThat(note.getDeleted()).isFalse();
        assertThat(note.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("삭제되지 않은 노트 복원 시 예외처리")
    void testRestoreNotDeleted() {
        // given
        Note note = createNote();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        // then
        assertThatThrownBy(() -> noteService.restore(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.NOT_DELETED.getMessage());
    }

    @Test
    @DisplayName("노트 완전 삭제")
    void testPermanentDelete() {
        // given
        Note note = createNote();
        note.softDelete();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        // when
        noteService.permanentDelete(1L);

        // then
        verify(noteRepository).delete(note);
    }

    @Test
    @DisplayName("삭제되지 않은 노트 완전 삭제 시 예외처리")
    void testPermanentDeleteNotDeleted() {
        // given
        Note note = createNote();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        // then
        assertThatThrownBy(() -> noteService.permanentDelete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.NOT_DELETED.getMessage());
    }

    @Test
    @DisplayName("노트 데이터가 없으면 예외처리(완전 삭제)")
    void testPermanentDeleteNotFound() {
        when(noteRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.permanentDelete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.RESOURCE_NOT_FOUND.getMessage());
    }

}
