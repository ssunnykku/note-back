package com.sun.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 100) String name) {

    public static CreateCategoryRequest of(String name) {
        return new CreateCategoryRequest(name);
    }
}
