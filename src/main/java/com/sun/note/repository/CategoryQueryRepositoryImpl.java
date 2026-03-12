package com.sun.note.repository;

import java.util.List;
import java.util.UUID;

import com.querydsl.core.group.GroupBy;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sun.note.domain.entity.QCategory;
import com.sun.note.domain.entity.QNote;
import com.sun.note.dto.CategoryNoteDto;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CategoryQueryRepositoryImpl implements CategoryQueryRepository {
        private final JPAQueryFactory queryFactory;

        @Override
        public List<CategoryNoteDto> findCategoriesNotesByUserId(UUID userId) {
                QCategory category = QCategory.category;
                QNote note = QNote.note;

                return queryFactory
                                .from(category)
                                .leftJoin(note).on(note.categoryId.eq(category.id))
                                .transform(GroupBy.groupBy(category.id)
                                                .list(Projections.constructor(CategoryNoteDto.class,
                                                                category.id,
                                                                category.name,
                                                                GroupBy.list(Projections.constructor(
                                                                                CategoryNoteDto.NoteDto.class,
                                                                                note.id,
                                                                                note.userId,
                                                                                note.title,
                                                                                note.content,
                                                                                note.createdAt,
                                                                                note.updatedAt)))));
        }

}
