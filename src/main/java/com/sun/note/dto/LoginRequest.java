package com.sun.note.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Email @Size(max = 255) String email,
        @NotBlank @Min(8) @Max(64) String password) {

    public static LoginRequest of(String email, String password) {
        return new LoginRequest(email, password);
    }
}