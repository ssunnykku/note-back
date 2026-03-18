package com.sun.note.service;

import java.util.*;
import org.springframework.stereotype.Service;

import com.sun.note.dto.CategoryNoteDto;
import com.sun.note.repository.CategoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;

    // 리스트 조회
    public List<CategoryNoteDto> getNoteList(UUID userId) {
        List<CategoryNoteDto> noteList = categoryRepository.findCategoriesNotesByUserId(userId);
        return noteList;
    }
}
