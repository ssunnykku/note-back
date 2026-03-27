package com.sun.note.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.sun.note.domain.entity.Note;
import com.sun.note.dto.CategoryNoteDto;
import com.sun.note.dto.NoteResponse;
import com.sun.note.dto.CategoryNoteDto.NoteDto;
import com.sun.note.repository.CategoryRepository;
import com.sun.note.repository.NoteRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CategoryNoteServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private NoteRepository noteRepository;

    @InjectMocks
    private CategoryNoteService categoryNoteService;

    @Test
    @DisplayName("카테고리별 노트 리스트 조회")
    void testGetNoteList() {
        // given
        UUID userId = UUID.randomUUID();

        CategoryNoteDto categoryNote1 = CategoryNoteDto.of(1L, "개발", 
                List.of(NoteDto.of(1L, userId, "자바", LocalDateTime.now(), LocalDateTime.now()), 
                        NoteDto.of(2L, userId, "스레드", LocalDateTime.now(), LocalDateTime.now()),
                        NoteDto.of(3L, userId, "스프링", LocalDateTime.now(), LocalDateTime.now())));
         CategoryNoteDto categoryNote2 = CategoryNoteDto.of(2L, "일기", 
                        List.of(NoteDto.of(3L, userId, "운동함", LocalDateTime.now(), LocalDateTime.now()), 
                                NoteDto.of(4L, userId, "공부함", LocalDateTime.now(), LocalDateTime.now())));
        List<CategoryNoteDto> list = List.of(categoryNote1, categoryNote2);

        when(categoryRepository.findCategoriesNotes(userId, false)).thenReturn(list);
        
         // when
        List<CategoryNoteDto> result = categoryNoteService.getNoteList(userId, false);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).notes()).hasSize(3);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).categoryName()).isEqualTo("개발");
        assertThat(result.get(0).notes().get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).notes().get(0).userId()).isEqualTo(userId);
        assertThat(result.get(0).notes().get(0).title()).isEqualTo("자바");
    }

    @Test
    @DisplayName("특정 카테고리의 노트 리스트 조회")
    void testGetNoteListByCategoryId() {
        // given
        UUID userId = UUID.randomUUID();
        List<Note> noteList = List.of(Note.of(102L, userId, 1L, "스레드", "스레드 공부"),
                        Note.of(103L, userId, 1L, "자바", "자바 공부"));
        
        when(noteRepository.findByUserIdAndCategoryIdAndDeleted(userId, 1L, false)).thenReturn(noteList);

        // when
        List<NoteResponse> result = categoryNoteService.getNoteListByCategoryId(userId, 1L, false);

        // then
        assertThat(result.get(0).categoryId()).isEqualTo(1L);
        assertThat(result.get(0).id()).isEqualTo(102L);
        assertThat(result.get(0).userId()).isEqualTo(userId);
        assertThat(result.get(0).title()).isEqualTo("스레드");

        assertThat(result.get(1).categoryId()).isEqualTo(1L);
        assertThat(result.get(1).id()).isEqualTo(103L);
        assertThat(result.get(1).userId()).isEqualTo(userId);
        assertThat(result.get(1).title()).isEqualTo("자바");
    }
    
    @Test
    @DisplayName("카테고리가 null일 경우 해당 리스트 조회_미분류노트")
    void testGetNoteListByCategoryIdNull() {
        // given
        UUID userId = UUID.randomUUID();
        List<Note> noteList = List.of(Note.of(102L, userId, null, "스레드", "스레드 공부"),
                        Note.of(103L, userId, null, "자바", "자바 공부"));
        
        when(noteRepository.findByUserIdAndCategoryIdAndDeleted(userId, null, false)).thenReturn(noteList);

        // when
        List<NoteResponse> result = categoryNoteService.getNoteListByCategoryId(userId, null, false);

        // then
        assertThat(result.get(0).categoryId()).isNull();
        assertThat(result.get(0).id()).isEqualTo(102L);
        assertThat(result.get(0).userId()).isEqualTo(userId);
        assertThat(result.get(0).title()).isEqualTo("스레드");

        assertThat(result.get(1).categoryId()).isNull();
        assertThat(result.get(1).id()).isEqualTo(103L);
        assertThat(result.get(1).userId()).isEqualTo(userId);
        assertThat(result.get(1).title()).isEqualTo("자바");

    }

    // 빈 리스트 반환 케이스
    @Test
    @DisplayName("카테고리별 노트 리스트 조회 - 노트가 없으면 빈 리스트 반환")
    void testGetNoteListEmpty() {
       // given
        UUID userId = UUID.randomUUID();

        CategoryNoteDto categoryNote1 = CategoryNoteDto.of(1L, "개발", List.of());
        CategoryNoteDto categoryNote2 = CategoryNoteDto.of(2L, "일기", List.of());
        List<CategoryNoteDto> list = List.of(categoryNote1, categoryNote2);

        when(categoryRepository.findCategoriesNotes(userId, false)).thenReturn(list);
        
         // when
        List<CategoryNoteDto> result = categoryNoteService.getNoteList(userId, false);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).notes()).hasSize(0);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(1).notes()).hasSize(0);
        assertThat(result.get(1).id()).isEqualTo(2L);
    }

    @Test
    @DisplayName("특정 카테고리의 노트 리스트 조회 - 노트가 없으면 빈 리스트 반환")
    void testGetNoteListByCategoryIdEmpty() {
    // given
        UUID userId = UUID.randomUUID();
        List<Note> noteList = List.of();
        
        when(noteRepository.findByUserIdAndCategoryIdAndDeleted(userId, 1L, false)).thenReturn(noteList);

        // when
        List<NoteResponse> result = categoryNoteService.getNoteListByCategoryId(userId, 1L, false);

        // then
        assertThat(result).hasSize(0);

    }

}
