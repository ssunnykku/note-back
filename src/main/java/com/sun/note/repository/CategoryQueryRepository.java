package com.sun.note.repository;

import com.sun.note.dto.CategoryNoteDto;

import java.util.List;
import java.util.UUID;

public interface CategoryQueryRepository {
    List<CategoryNoteDto> findCategoriesNotesByUserId(UUID userId, boolean deleted);

}
