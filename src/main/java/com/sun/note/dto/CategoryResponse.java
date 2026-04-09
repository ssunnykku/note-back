package com.sun.note.dto;

import com.sun.note.domain.entity.Category;

public record CategoryResponse(
        Long id,
        String name) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
