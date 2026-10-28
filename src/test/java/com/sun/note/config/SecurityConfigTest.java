package com.sun.note.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.sun.note.controller.AuthController;
import com.sun.note.controller.ChatController;
import com.sun.note.exception.BusinessException;
import com.sun.note.exception.ErrorCode;
import com.sun.note.service.UserService;
import com.sun.note.service.ChatService;
import com.sun.note.util.JwtUtil;

// 실제 SecurityConfig + JwtFilter로 인증 실패 응답 형식을 검증한다.
@WebMvcTest({ ChatController.class, AuthController.class })
@Import({ SecurityConfig.class, SecurityConfigTest.JwtTestConfig.class })
class SecurityConfigTest {

    private static final String SECRET = "8+T8u4RuiMFnulypsZTC/jVrSDwAYERM0GQ7R9B+Mfg=";
    private static final String USER_ID = "2013a306-9369-46ba-ac55-2f547ac5c50f";

    @TestConfiguration
    static class JwtTestConfig {
        @Bean
        JwtUtil jwtUtil() {
            return new JwtUtil(SECRET, 3600000);
        }

        @Bean
        AuthProperties authProperties() {
            return new AuthProperties(new AuthProperties.RefreshToken(Duration.ofDays(7), false));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @MockBean
    private UserService userService;

    @Test
    @DisplayName("토큰이 없으면 401 UNAUTHORIZED ProblemDetail")
    void noToken_401() throws Exception {
        mockMvc.perform(get("/api/chats"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/chats"));
    }

    @Test
    @DisplayName("만료된 토큰이면 401 TOKEN_EXPIRED")
    void expiredToken_401_tokenExpired() throws Exception {
        String expired = new JwtUtil(SECRET, 0).generateToken(USER_ID);

        mockMvc.perform(get("/api/chats").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("다른 키로 서명된 만료 토큰은 만료가 아니라 UNAUTHORIZED")
    void forgedExpiredToken_401_unauthorized() throws Exception {
        String forged = new JwtUtil("Mfj6zEMpm3cbMQDPr/eYZlXNm07MERgQotg1qm824kw=", 0).generateToken(USER_ID);

        mockMvc.perform(get("/api/chats").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("유효한 토큰이면 통과")
    void validToken_ok() throws Exception {
        String token = new JwtUtil(SECRET, 3600000).generateToken(USER_ID);

        mockMvc.perform(get("/api/chats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("인증 실패 응답에서 HTTP 세션을 만들지 않는다 (JWT 기반 stateless)")
    void unauthorized_noSession() throws Exception {
        mockMvc.perform(get("/api/chats"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    @Test
    @DisplayName("서명은 올바르지만 subject가 UUID가 아닌 토큰은 500이 아니라 401 UNAUTHORIZED")
    void nonUuidSubject_401() throws Exception {
        String token = new JwtUtil(SECRET, 3600000).generateToken("not-a-uuid");

        mockMvc.perform(get("/api/chats").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("refresh는 액세스 토큰 없이 호출할 수 있다 (인증 실패가 아니라 refresh 토큰 검증 결과로 응답)")
    void refresh_permitAll() throws Exception {
        doThrow(new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)).when(userService).refresh(null);

        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("logout은 액세스 토큰 없이(만료 후에도) 호출할 수 있다")
    void logout_permitAll() throws Exception {
        String expired = new JwtUtil(SECRET, 0).generateToken(USER_ID);

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + expired))
                .andExpect(status().isNoContent());
    }
}
