package com.sun.note.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// AI 채팅 사용량 제한 설정 (chat.*)
@ConfigurationProperties(prefix = "chat")
public record ChatProperties(
        @DefaultValue History history,
        @DefaultValue RateLimit rateLimit) {

    // AI에 함께 보내는 이전 대화 범위. 대화 전체를 보내면 모델의 컨텍스트(num_ctx)를 넘어
    // Ollama가 앞부분을 잘라 버리고 응답도 느려지므로, 최근 메시지만 개수와 글자 수 안에서 보낸다.
    public record History(
            @DefaultValue("20") int maxMessages,
            @DefaultValue("12000") int maxChars) {
    }

    // 사용자별 전송 횟수 제한: window 동안 최대 maxRequests회
    public record RateLimit(
            @DefaultValue("10") int maxRequests,
            @DefaultValue("2m") Duration window) {
    }
}
