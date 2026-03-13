package com.sun.note.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sun.note.domain.entity.QCategory;
import com.sun.note.domain.entity.QNote;
import com.sun.note.dto.CategoryNoteDto;
import com.sun.note.dto.CategoryNoteDto.NoteDto;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CategoryQueryRepositoryImpl implements CategoryQueryRepository {
        private final JPAQueryFactory queryFactory;

        @Override
        public List<CategoryNoteDto> findCategoriesNotesByUserId(UUID userId) {
                QCategory category = QCategory.category;
                QNote note = QNote.note;

        List<Tuple> results = queryFactory
                .select(category.id, category.name,
                        note.id, note.userId, note.title, note.createdAt, note.updatedAt)
                .from(category)
                .leftJoin(note).on(note.categoryId.eq(category.id).and(note.userId.eq(userId)))
                .orderBy(category.id.asc())
                .fetch();

        Map<Long, List<Tuple>> grouped = results.stream()
                .collect(Collectors.groupingBy(t -> t.get(category.id), Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<Tuple> tuples = entry.getValue();
                    String categoryName = tuples.get(0).get(category.name);

                    List<NoteDto> notes = tuples.stream()
                            .filter(t -> t.get(note.id) != null)
                            .map(t -> NoteDto.of(
                                    t.get(note.id),
                                    t.get(note.userId),
                                    t.get(note.title),
                                    t.get(note.createdAt),
                                    t.get(note.updatedAt)))
                            .toList();

                    return CategoryNoteDto.of(entry.getKey(), categoryName, notes);
                })
                .toList();
    }
}
