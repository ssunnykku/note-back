package com.sun.note.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.sun.note.domain.entity.User;
import com.sun.note.dto.LoginResponse;
import com.sun.note.service.UserService.AuthResult;
import com.sun.note.exception.BusinessException;
import com.sun.note.exception.ErrorCode;
import com.sun.note.repository.UserRepository;
import com.sun.note.util.JwtUtil;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UserService userService;

    private static final UUID USER_ID = UUID.fromString("2013a306-9369-46ba-ac55-2f547ac5c50f");
    private static final String NAME = "사용자";
    private static final String EMAIL = "test@gmail.com";
    private static final String PASSWORD = "rawPassword";
    private static final String ENCODED_PASSWORD = "encodedPassword";

    private User createUser() {
        return User.of(USER_ID, NAME, EMAIL, ENCODED_PASSWORD);
    }

    @Nested
    @DisplayName("회원가입")
    class SignUp {

        @Test
        @DisplayName("회원가입 성공 - 정상적인 이메일과 비밀번호로 회원가입")
        void signUp_success() {
            // given
            User userResult = createUser();

            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(userRepository.saveAndFlush(any(User.class))).thenReturn(userResult);
            when(refreshTokenService.issue(USER_ID)).thenReturn("refreshToken");

            // when
            AuthResult result = userService.signUp(NAME, EMAIL, PASSWORD, PASSWORD);

            // then
            LoginResponse res = result.response();
            assertThat(res).isNotNull();
            assertThat(res.user().email()).isEqualTo(EMAIL);
            assertThat(result.refreshToken()).isEqualTo("refreshToken");
            verify(passwordEncoder).encode(any());
            verify(userRepository).saveAndFlush(any(User.class));

        }

        @Test
        @DisplayName("회원가입 실패 - 이미 존재하는 이메일로 가입 시도")
        void signUp_fail_duplicateEmail() {
            // given
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> userService.signUp(NAME, EMAIL, PASSWORD, PASSWORD))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.EMAIL_ALREADY_EXISTS.getMessage());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("회원가입 실패 - 비밀번호가 유효성 검증에 실패")
        void signUp_fail_invalidPassword() {
            // given
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> userService.signUp(NAME, EMAIL, "123", "456"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.INVALID_PASSWORD.getMessage());
        }
    }

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("로그인 성공 - 올바른 이메일과 비밀번호로 로그인")
        void login_success() {
            // given
            User userResult = createUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userResult));
            when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
            when(jwtUtil.generateToken(USER_ID.toString())).thenReturn("accessToken");
            when(refreshTokenService.issue(USER_ID)).thenReturn("refreshToken");

            // when
            AuthResult result = userService.login(EMAIL, PASSWORD);

            // then
            LoginResponse res = result.response();
            assertThat(res).isNotNull();
            assertThat(result.refreshToken()).isEqualTo("refreshToken");
            assertThat(res.accessToken()).isEqualTo("accessToken");
            assertThat(res.user().email()).isEqualTo(EMAIL);
            verify(passwordEncoder).matches(PASSWORD, ENCODED_PASSWORD);
        }

        @Test
        @DisplayName("로그인 실패 - 존재하지 않는 이메일")
        void login_fail_emailNotFound() {
            // given
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.INVALID_EMAIL.getMessage());
        }

        @Test
        @DisplayName("로그인 실패 - 비밀번호 불일치")
        void login_fail_wrongPassword() {
            // given
            User userResult = createUser();
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userResult));
            when(passwordEncoder.matches(PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> userService.login(EMAIL, PASSWORD))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.INVALID_PASSWORD.getMessage());
            verify(jwtUtil, never()).generateToken(any());
            verify(refreshTokenService, never()).issue(any());
        }
    }

    @Nested
    @DisplayName("토큰 갱신")
    class Refresh {

        @Test
        @DisplayName("refresh 토큰을 회전하고 새 액세스 토큰과 사용자 정보를 반환한다")
        void refresh_success() {
            // given
            when(refreshTokenService.rotate("oldRefresh"))
                    .thenReturn(new RefreshTokenService.Rotated(USER_ID, "newRefresh"));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(createUser()));
            when(jwtUtil.generateToken(USER_ID.toString())).thenReturn("newAccess");

            // when
            AuthResult result = userService.refresh("oldRefresh");

            // then
            assertThat(result.response().accessToken()).isEqualTo("newAccess");
            assertThat(result.response().user().email()).isEqualTo(EMAIL);
            assertThat(result.refreshToken()).isEqualTo("newRefresh");
        }

        @Test
        @DisplayName("토큰의 사용자가 없으면 INVALID_REFRESH_TOKEN")
        void refresh_userNotFound() {
            // given
            when(refreshTokenService.rotate("oldRefresh"))
                    .thenReturn(new RefreshTokenService.Rotated(USER_ID, "newRefresh"));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.refresh("oldRefresh"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage(ErrorCode.INVALID_REFRESH_TOKEN.getMessage());
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("refresh 토큰을 폐기한다")
        void logout_revokes() {
            userService.logout("refresh");

            verify(refreshTokenService).revoke("refresh");
        }
    }

}
