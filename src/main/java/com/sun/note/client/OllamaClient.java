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

    public OllamaClient(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.connect-timeout-seconds:5}") long connectTimeoutSeconds,
            @Value("${ollama.read-timeout-seconds:120}") long readTimeoutSeconds) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(connectTimeoutSeconds).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(readTimeoutSeconds).toMillis());

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public String chat(String model, List<OllamaMessage> messages) {
        try {
            OllamaChatResponse response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new OllamaChatRequest(model, messages, false))
                    .retrieve()
                    .body(OllamaChatResponse.class);

            if (response == null || response.message() == null) {
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            }

            return response.message().content();
        } catch (RestClientException e) {
            log.error("Ollama 호출 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }
}
