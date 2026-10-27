package com.sun.note.dto;

import jakarta.validation.constraints.Size;

// modelId는 선택값. 생략하면 첫 메시지 전송 시 지정된다.
public record CreateChatRoomRequest(
        @Size(max = 100) String modelId) {

    public static CreateChatRoomRequest of(String modelId) {
        return new CreateChatRoomRequest(modelId);
    }
}
