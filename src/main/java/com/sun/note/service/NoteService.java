package com.sun.note.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sun.note.domain.entity.Note;
import com.sun.note.dto.NoteResponse;
import com.sun.note.exception.BusinessException;
import com.sun.note.exception.ErrorCode;
import com.sun.note.repository.NoteRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NoteService {
    private final NoteRepository noteRepository;

    // 생성
    @Transactional
    public NoteResponse addNote(UUID userId, Long categoryId, String title, String content) {
        Note note = noteRepository.save(Note.of(userId, categoryId, title, content));

        NoteResponse noteResponse = NoteResponse.of(note.getId(), note.getUserId(), note.getCategoryId(),
                note.getTitle(), note.getContent(), note.getCreatedAt(), note.getUpdatedAt());
        return noteResponse;
    }

    // 수정
    @Transactional
    public NoteResponse editNote(Long id, Long categoryId, String title, String content) {
        Note note = noteRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        note.edit(categoryId, title, content);
        return NoteResponse.of(note.getId(), note.getUserId(), note.getCategoryId(),
                note.getTitle(), note.getContent(), note.getCreatedAt(), note.getUpdatedAt());
    }

    // 조회
    public NoteResponse getById(Long id) {
        Note note = noteRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        NoteResponse noteResponse = NoteResponse.of(note.getId(), note.getUserId(), note.getCategoryId(),
                note.getTitle(), note.getContent(), note.getCreatedAt(), note.getUpdatedAt());
        return noteResponse;
    }

}
