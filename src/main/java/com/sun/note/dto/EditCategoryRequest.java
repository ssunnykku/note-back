package com.sun.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditCategoryRequest(
        @NotBlank @Size(max = 100) String name
) {

    public static EditCategoryRequest of(String name) {
        return new EditCategoryRequest(name);
    }
}
