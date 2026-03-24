package com.sun.note.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sun.note.dto.CategoryNoteDto;
import com.sun.note.service.CategoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categoryService;

    // 조회(리스트)
    @GetMapping("{userId}/notes")
    ResponseEntity<List<CategoryNoteDto>> getNoteList(
            @PathVariable("userId") UUID userId,
            @RequestParam(value = "deleted", defaultValue = "false") boolean deleted) {
        List<CategoryNoteDto> list = categoryService.getNoteList(userId, deleted);
        return ResponseEntity.ok().body(list);
    }

}
