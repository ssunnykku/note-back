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
            when(userRepository.save(any(User.class))).thenReturn(userResult);

            // when
            LoginResponse res = userService.signUp(NAME, EMAIL, PASSWORD, PASSWORD);

            // then
            assertThat(res).isNotNull();
            assertThat(res.user().email()).isEqualTo(EMAIL);
            verify(passwordEncoder).encode(any());
            verify(userRepository).save(any(User.class));

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

            // when
            LoginResponse res = userService.login(EMAIL, PASSWORD);

            // then
            assertThat(res).isNotNull();
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
        }
    }

}
