package com.sun.note.client;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.note.exception.BusinessException;
import com.sun.note.exception.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaClientTest {

    @Test
    @DisplayName("응답 대기 timeout이면 503으로 바꾸되, 원인(SocketTimeoutException)을 함께 남긴다")
    void readTimeout_keepsCause() throws Exception {
        // 연결은 받지만 응답하지 않는 서버
        try (ServerSocket server = new ServerSocket(0)) {
            Thread acceptor = new Thread(() -> {
                try (Socket ignored = server.accept()) {
                    Thread.sleep(5000);
                } catch (Exception ignored) {
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            OllamaClient client = new OllamaClient("http://localhost:" + server.getLocalPort(), 1, 1);

            assertThatThrownBy(() -> client.chat("qwen3:8b", List.of(new OllamaMessage("user", "안녕?"))))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE);
                        assertThat(e).hasRootCauseInstanceOf(SocketTimeoutException.class);
                    });
        }
    }

    @Test
    @DisplayName("최대 호출 시간은 connect + read timeout")
    void maxCallDuration() {
        OllamaClient client = new OllamaClient("http://localhost:1", 5, 120);

        assertThat(client.getMaxCallDuration()).hasSeconds(125);
    }
}
