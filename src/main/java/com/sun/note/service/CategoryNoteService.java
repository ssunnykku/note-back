package com.sun.note.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sun.note.domain.entity.Note;
import com.sun.note.dto.CategoryNoteDto;
import com.sun.note.dto.NoteResponse;
import com.sun.note.repository.CategoryRepository;
import com.sun.note.repository.NoteRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryNoteService {
    private final CategoryRepository categoryRepository;
    private final NoteRepository noteRepository;

    // 카테고리별 노트 리스트 조회(전체)
    public List<CategoryNoteDto> getNoteList(UUID userId, boolean deleted) {
         List<CategoryNoteDto> noteList = categoryRepository.findCategoriesNotes(userId, deleted);
        return noteList;
    }

    // 특정 카테고리의 노트 리스트 조회
    public List<NoteResponse> getNoteListbyCategoryId(UUID userId, Long categoryId, boolean deleted) {
        List<Note> noteList = noteRepository.findByUserIdAndCategoryIdAndDeleted(userId, categoryId, deleted);
        
        List<NoteResponse> response = noteList
                .stream()
                .map(note -> 
                NoteResponse.of(note.getId(), note.getUserId(),
                        note.getCategoryId(), note.getTitle(),
                        note.getContent(),
                        note.getCreatedAt(),
                        note.getUpdatedAt())).toList();
        return response;
    }
}
