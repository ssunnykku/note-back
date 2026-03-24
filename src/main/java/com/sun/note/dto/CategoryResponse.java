package com.sun.note.dto;

public record CategoryResponse(
        Long id,
        String name
) {
    public static CategoryResponse of(Long id, String name) {
        return new CategoryResponse(id, name);
    }
}
