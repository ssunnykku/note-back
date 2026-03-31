package com.sun.note.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sun.note.dto.CreateNoteRequest;
import com.sun.note.dto.EditNoteRequest;
import com.sun.note.dto.NoteResponse;
import com.sun.note.service.NoteService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notes")
public class NoteController {
    private final NoteService noteService;

    // 생성
    @PostMapping
    ResponseEntity<NoteResponse> addNote(@Valid @RequestBody CreateNoteRequest dto, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        NoteResponse note = noteService.addNote(userId, dto.categoryId(), dto.title(), dto.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(note);
    }

    // 수정
    @PutMapping("{id}")
    ResponseEntity<NoteResponse> editNote(@PathVariable("id") Long id, @Valid @RequestBody EditNoteRequest dto) {
        NoteResponse note = noteService.editNote(id, dto.categoryId(), dto.title(), dto.content());
        return ResponseEntity.ok(note);
    }

    // 조회
    @GetMapping("{id}")
    ResponseEntity<NoteResponse> getNote(@PathVariable("id") Long id) {
        NoteResponse note = noteService.getById(id);
        return ResponseEntity.ok().body(note);
    }

    // 휴지통으로 이동 (soft delete)
    @DeleteMapping("{id}")
    ResponseEntity<Void> softDeleteNote(@PathVariable("id") Long id) {
        noteService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    // 복원
    @PatchMapping("{id}/restore")
    ResponseEntity<Void> restoreNote(@PathVariable("id") Long id) {
        noteService.restore(id);
        return ResponseEntity.noContent().build();
    }

    // 완전 삭제 (hard delete)
    @DeleteMapping("{id}/permanent")
    ResponseEntity<Void> permanentDeleteNote(@PathVariable("id") Long id) {
        noteService.permanentDelete(id);
        return ResponseEntity.noContent().build();
    }

}
