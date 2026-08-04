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
                note.getTitle(), note.getContent(), note.getCreatedAt(), note.getUpdatedAt(), note.getVersion());
        return noteResponse;
    }

    // 수정
    @Transactional
    public NoteResponse editNote(Long id, Long categoryId, String title, String content, Long version) {
        Note note = findActiveNote(id);
        if (!note.getVersion().equals(version)) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        note.edit(categoryId, title, content);
        Note saved = noteRepository.saveAndFlush(note);
        return NoteResponse.of(saved.getId(), saved.getUserId(), saved.getCategoryId(),
                saved.getTitle(), saved.getContent(), saved.getCreatedAt(), saved.getUpdatedAt(), saved.getVersion());
    }

    // 조회
    public NoteResponse getById(Long id) {
        Note note = findActiveNote(id);
        return NoteResponse.of(note.getId(), note.getUserId(), note.getCategoryId(),
                note.getTitle(), note.getContent(), note.getCreatedAt(), note.getUpdatedAt(), note.getVersion());
    }

    // 휴지통으로 이동 (soft delete)
    @Transactional
    public void softDelete(Long id) {
        Note note = noteRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (note.getDeleted()) {
            throw new BusinessException(ErrorCode.ALREADY_DELETED);
        }
        note.softDelete();
    }

    // 복원
    @Transactional
    public void restore(Long id) {
        Note note = noteRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!note.getDeleted()) {
            throw new BusinessException(ErrorCode.NOT_DELETED);
        }
        note.restore();
    }

    // 완전 삭제 (hard delete)
    @Transactional
    public void permanentDelete(Long id) {
        Note note = noteRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!note.getDeleted()) {
            throw new BusinessException(ErrorCode.NOT_DELETED);
        }
        noteRepository.delete(note);
    }

    private Note findActiveNote(Long id) {
        Note note = noteRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (note.getDeleted()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return note;
    }

}
