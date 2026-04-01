package com.sun.note.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 64) String password) {

    public static LoginRequest of(String email, String password) {
        return new LoginRequest(email, password);
    }
}