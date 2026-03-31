package com.sun.note.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sun.note.dto.CategoryNoteDto;
import com.sun.note.dto.CategoryResponse;
import com.sun.note.dto.CreateCategoryRequest;
import com.sun.note.dto.EditCategoryRequest;
import com.sun.note.dto.NoteResponse;
import com.sun.note.service.CategoryNoteService;
import com.sun.note.service.CategoryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categoryService;
    private final CategoryNoteService categoryNoteService;

    // 생성
    @PostMapping
    ResponseEntity<CategoryResponse> addCategory(@Valid @RequestBody CreateCategoryRequest dto) {
        CategoryResponse category = categoryService.addCategory(dto.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(category);
    }

    // 수정
    @PatchMapping("{id}")
    ResponseEntity<CategoryResponse> editCategory(
            @PathVariable("id") Long id,
            @Valid @RequestBody EditCategoryRequest dto) {
        CategoryResponse category = categoryService.editCategory(id, dto.name());
        return ResponseEntity.ok().body(category);
    }

    // 조회(리스트)
    @GetMapping("notes")
    ResponseEntity<List<CategoryNoteDto>> getNoteList(
            Authentication auth,
            @RequestParam(value = "deleted", defaultValue = "false") boolean deleted) {
        UUID userId = UUID.fromString(auth.getName());
        List<CategoryNoteDto> list = categoryNoteService.getNoteList(userId, deleted);
        return ResponseEntity.ok().body(list);
    }

    // 노트 리스트 조회 by categoryId
    @GetMapping
    ResponseEntity<List<NoteResponse>> getNoteListByCategoryId(
            Authentication auth,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "deleted", defaultValue = "false") boolean deleted) {
        UUID userId = UUID.fromString(auth.getName());
        List<NoteResponse> note = categoryNoteService.getNoteListByCategoryId(userId, categoryId, deleted);
        return ResponseEntity.ok().body(note);
    }

}
