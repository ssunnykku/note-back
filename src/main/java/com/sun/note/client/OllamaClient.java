package com.sun.note.client;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sun.note.exception.BusinessException;
import com.sun.note.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

// 자체 호스팅 중인 Ollama 서버(/api/chat)에 메시지 히스토리를 전달하고 응답을 받아온다.
@Slf4j
@Component
public class OllamaClient {

    private final RestClient restClient;
    private final Duration maxCallDuration;

    public OllamaClient(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.connect-timeout-seconds:5}") long connectTimeoutSeconds,
            @Value("${ollama.read-timeout-seconds:120}") long readTimeoutSeconds) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(connectTimeoutSeconds).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(readTimeoutSeconds).toMillis());

        this.maxCallDuration = Duration.ofSeconds(connectTimeoutSeconds + readTimeoutSeconds);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    // 한 번의 호출이 timeout으로 끝나기까지 걸릴 수 있는 최대 시간 (connect + read)
    public Duration getMaxCallDuration() {
        return maxCallDuration;
    }

    public String chat(String model, List<OllamaMessage> messages) {
        try {
            OllamaChatResponse response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new OllamaChatRequest(model, messages, false))
                    .retrieve()
                    .body(OllamaChatResponse.class);

            // content 없는 응답(예: tool_calls만 온 경우)도 실패로 본다. 그대로 저장하면 NOT NULL 위반으로 500이 된다.
            if (response == null || response.message() == null
                    || response.message().content() == null || response.message().content().isBlank()) {
                log.error("Ollama 응답에 content가 없음: model={}", model);
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            }

            return response.message().content();
        } catch (RestClientException e) {
            // 응답 대기 중 timeout은 "응답 추출 실패"로 감싸져 오므로, 가장 안쪽 원인(예: SocketTimeoutException)을 함께 남긴다.
            log.error("Ollama 호출 실패: model={}, cause={}", model, e.getMostSpecificCause().toString());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE, e);
        }
    }
}
