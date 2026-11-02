package com.sun.note.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// AI 채팅 사용량 제한 설정 (chat.*)
@ConfigurationProperties(prefix = "chat")
public record ChatProperties(
        @DefaultValue History history,
        @DefaultValue RateLimit rateLimit) {

    // 대화 전체를 보내면 모델 컨텍스트(num_ctx)를 넘어 Ollama가 앞부분을 자르고 응답도 느려진다
    public record History(
            @DefaultValue("20") int maxMessages,
            @DefaultValue("12000") int maxChars) {
    }

    public record RateLimit(
            @DefaultValue("10") int maxRequests,
            @DefaultValue("2m") Duration window) {
    }
}
