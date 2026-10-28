package com.sun.note.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.sun.note.config.AuthProperties;
import com.sun.note.config.SecurityConfig;
import com.sun.note.dto.LoginResponse;
import com.sun.note.exception.BusinessException;
import com.sun.note.exception.ErrorCode;
import com.sun.note.filter.JwtFilter;
import com.sun.note.service.UserService;
import com.sun.note.service.UserService.AuthResult;

import jakarta.servlet.http.Cookie;

@WebMvcTest(value = AuthController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        SecurityConfig.class, JwtFilter.class }))
@Import(AuthControllerTest.AuthPropertiesConfig.class)
@WithMockUser
class AuthControllerTest {

    @TestConfiguration
    static class AuthPropertiesConfig {
        @Bean
        AuthProperties authProperties() {
            return new AuthProperties(new AuthProperties.RefreshToken(Duration.ofDays(7), true));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    private static final AuthResult RESULT = new AuthResult(
            LoginResponse.of("accessToken", LoginResponse.UserInfo.of("사용자", "test@gmail.com")), "refreshToken");

    private static final String LOGIN_BODY = """
            { "email": "test@gmail.com", "password": "password1234" }
            """;

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("액세스 토큰은 본문으로, refresh 토큰은 HttpOnly 쿠키로 내려준다")
        void login_setsRefreshCookie() throws Exception {
            when(userService.login(anyString(), anyString())).thenReturn(RESULT);

            mockMvc.perform(post("/api/auth/login").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("accessToken"))
                    .andExpect(jsonPath("$.user.email").value("test@gmail.com"))
                    .andExpect(jsonPath("$.refreshToken").doesNotExist())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                            containsString("refresh_token=refreshToken"),
                            containsString("Path=/api/auth"),
                            containsString("Max-Age=604800"),
                            containsString("HttpOnly"),
                            containsString("Secure"),
                            containsString("SameSite=Strict"))));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/signup")
    class Signup {

        @Test
        @DisplayName("가입 후 바로 로그인 상태가 되도록 refresh 쿠키를 내려준다")
        void signup_setsRefreshCookie() throws Exception {
            when(userService.signUp(anyString(), anyString(), anyString(), anyString())).thenReturn(RESULT);

            mockMvc.perform(post("/api/auth/signup").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "name": "사용자", "email": "test@gmail.com",
                                      "password": "password1234", "passwordConfirm": "password1234" }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=refreshToken")));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/refresh")
    class Refresh {

        @Test
        @DisplayName("쿠키의 refresh 토큰으로 새 액세스 토큰을 발급하고 쿠키를 회전한다")
        void refresh_success() throws Exception {
            doReturn(new AuthResult(RESULT.response(), "newRefresh")).when(userService).refresh("oldRefresh");

            mockMvc.perform(post("/api/auth/refresh").with(csrf()).cookie(new Cookie("refresh_token", "oldRefresh")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("accessToken"))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=newRefresh")));
        }

        @Test
        @DisplayName("refresh 토큰이 무효면 401 INVALID_REFRESH_TOKEN, 쿠키는 건드리지 않는다")
        void refresh_invalid_keepsCookie() throws Exception {
            // 다른 탭이 먼저 회전해 받은 새 쿠키를 늦게 도착한 실패 응답이 지우면 둘 다 로그아웃된다. (쿠키 저장소는 탭 간 공유)
            doThrow(new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)).when(userService).refresh("bad");

            mockMvc.perform(post("/api/auth/refresh").with(csrf()).cookie(new Cookie("refresh_token", "bad")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("INVALID_REFRESH_TOKEN"))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }

        @Test
        @DisplayName("쿠키 값에 잘못된 % 인코딩이 있어도 500이 아니라 401")
        void refresh_malformedCookie() throws Exception {
            doThrow(new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)).when(userService).refresh("%%%");

            mockMvc.perform(post("/api/auth/refresh").with(csrf()).cookie(new Cookie("refresh_token", "%%%")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("INVALID_REFRESH_TOKEN"));
        }

        @Test
        @DisplayName("쿠키가 없으면 null로 위임해 401")
        void refresh_noCookie() throws Exception {
            doThrow(new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)).when(userService).refresh(null);

            mockMvc.perform(post("/api/auth/refresh").with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.title").value("INVALID_REFRESH_TOKEN"));
        }
    }

    @Nested
    @DisplayName("POST /api/auth/logout")
    class Logout {

        @Test
        @DisplayName("refresh 토큰을 폐기하고 쿠키를 지운 뒤 204")
        void logout() throws Exception {
            mockMvc.perform(post("/api/auth/logout").with(csrf()).cookie(new Cookie("refresh_token", "token")))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

            verify(userService).logout("token");
        }

        @Test
        @DisplayName("쿠키 값에 잘못된 % 인코딩이 있어도 204")
        void logout_malformedCookie() throws Exception {
            mockMvc.perform(post("/api/auth/logout").with(csrf()).cookie(new Cookie("refresh_token", "abc%")))
                    .andExpect(status().isNoContent());

            verify(userService).logout("abc%");
        }
    }
}
