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

    @Transactional
    public NoteResponse addNote(UUID userId, Long categoryId, String title, String content) {
        Note note = noteRepository.save(Note.of(userId, categoryId, title, content));

        NoteResponse noteResponse = NoteResponse.of(note.getId(), note.getUserId(), note.getCategoryId(),
                note.getTitle(), note.getContent(), note.getCreatedAt(), note.getUpdatedAt(), note.getVersion());
        return noteResponse;
    }

    @Transactional
    public NoteResponse editNote(Long id, UUID userId, Long categoryId, String title, String content, Long version) {
        Note note = findActiveNote(id, userId);
        if (!note.getVersion().equals(version)) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        note.edit(categoryId, title, content);
        Note saved = noteRepository.saveAndFlush(note);
        return NoteResponse.of(saved.getId(), saved.getUserId(), saved.getCategoryId(),
                saved.getTitle(), saved.getContent(), saved.getCreatedAt(), saved.getUpdatedAt(), saved.getVersion());
    }

    public NoteResponse getById(Long id, UUID userId) {
        Note note = findActiveNote(id, userId);
        return NoteResponse.of(note.getId(), note.getUserId(), note.getCategoryId(),
                note.getTitle(), note.getContent(), note.getCreatedAt(), note.getUpdatedAt(), note.getVersion());
    }

    @Transactional
    public void softDelete(Long id, UUID userId) {
        Note note = findOwnedNote(id, userId);
        if (note.getDeleted()) {
            throw new BusinessException(ErrorCode.ALREADY_DELETED);
        }
        note.softDelete();
    }

    @Transactional
    public void restore(Long id, UUID userId) {
        Note note = findOwnedNote(id, userId);
        if (!note.getDeleted()) {
            throw new BusinessException(ErrorCode.NOT_DELETED);
        }
        note.restore();
    }

    @Transactional
    public void permanentDelete(Long id, UUID userId) {
        Note note = findOwnedNote(id, userId);
        if (!note.getDeleted()) {
            throw new BusinessException(ErrorCode.NOT_DELETED);
        }
        noteRepository.delete(note);
    }

    private Note findActiveNote(Long id, UUID userId) {
        Note note = findOwnedNote(id, userId);
        if (note.getDeleted()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return note;
    }

    // 다른 사용자의 노트는 존재 여부를 드러내지 않도록 없는 노트와 같이 404로 응답한다
    private Note findOwnedNote(Long id, UUID userId) {
        Note note = noteRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!note.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return note;
    }

}
