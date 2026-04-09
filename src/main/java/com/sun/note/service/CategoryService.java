package com.sun.note.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sun.note.domain.entity.Category;
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
    public CategoryResponse addCategory(String name, UUID userId) {
        Category category = categoryRepository.save(Category.of(name, userId));
        return CategoryResponse.from(category);
    }

    // 수정
    @Transactional
    public CategoryResponse editCategory(Long id, String name, UUID userId) {
        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        category.editName(name);
        return CategoryResponse.from(category);
    }


}
