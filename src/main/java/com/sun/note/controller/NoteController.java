package com.sun.note.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequiredArgsConstructor
@RequestMapping("/notes")
public class NoteController {
    private final NoteService noteService;

    // 생성
    @PostMapping
    ResponseEntity<NoteResponse> addNote(@Valid @RequestBody CreateNoteRequest dto) {
        NoteResponse note = noteService.addNote(dto.userId(), dto.categoryId(), dto.title(), dto.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(note);
    }

    // 수정
    @PutMapping("{id}")
    ResponseEntity<Void> editNote(@PathVariable("id") Long id, @Valid @RequestBody EditNoteRequest dto) {
        noteService.editNote(id, dto.categoryId(), dto.title(), dto.content());
        return ResponseEntity.noContent().build();
    }

    // 조회
    @GetMapping("{id}")
    ResponseEntity<NoteResponse> getNote(@PathVariable("id") Long id) {
        NoteResponse note = noteService.findById(id);
        return ResponseEntity.ok().body(note);
    }

}
