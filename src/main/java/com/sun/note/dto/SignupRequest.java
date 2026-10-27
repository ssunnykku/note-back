package com.sun.note.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 엔티티(User) 제약과 동일하게 맞춰, 저장 시점이 아닌 요청 단계에서 400으로 거른다.
public record SignupRequest(
        @NotBlank @Size(min = 2, max = 50) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank String passwordConfirm) {
}
