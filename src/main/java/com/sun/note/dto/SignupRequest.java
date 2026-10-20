package com.sun.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        String name,
        String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank String passwordConfirm) {
}
