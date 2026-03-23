package com.sun.note.service;

import java.util.*;
import org.springframework.stereotype.Service;

import com.sun.note.domain.entity.Category;
import com.sun.note.dto.CategoryNoteDto;
import com.sun.note.dto.CategoryResponse;
import com.sun.note.exception.BusinessException;
import com.sun.note.exception.ErrorCode;
import com.sun.note.repository.CategoryRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;

    // 생성
    @Transactional
    public CategoryResponse addCategory(String name) {
        Category category = categoryRepository.save(Category.of(name));
        return CategoryResponse.of(category.getId(), category.getName());
    }

    // 수정
    @Transactional
    public CategoryResponse editCategory(Long id, String name) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        category.editName(name);
        return CategoryResponse.of(category.getId(), category.getName());
    }

    // 리스트 조회
    public List<CategoryNoteDto> getNoteList(UUID userId) {
        List<CategoryNoteDto> noteList = categoryRepository.findCategoriesNotesByUserId(userId);
        return noteList;
    }
}
