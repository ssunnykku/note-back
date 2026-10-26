package com.sun.note.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank @Size(max = 10000) String content,
        @NotBlank @Size(max = 100) String modelId) {

    public static SendMessageRequest of(String content, String modelId) {
        return new SendMessageRequest(content, modelId);
    }
}
