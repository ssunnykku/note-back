package com.sun.note.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.sun.note.domain.entity.User;
import com.sun.note.dto.LoginResponse;
import com.sun.note.exception.BusinessException;
import com.sun.note.exception.ErrorCode;
import com.sun.note.repository.UserRepository;
import com.sun.note.util.JwtUtil;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    // response는 응답 본문, refreshToken은 컨트롤러가 HttpOnly 쿠키로만 내려준다.
    public record AuthResult(LoginResponse response, String refreshToken) {
    }

    @Transactional
    public AuthResult signUp(String name, String email, String password, String passwordConfirm) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        if (!password.equals(passwordConfirm)) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }
        String encodedPassword = passwordEncoder.encode(password);

        // refresh_tokens가 users를 참조하므로 토큰 저장 전에 사용자 insert를 먼저 반영한다.
        User result = userRepository.saveAndFlush(User.of(name, email, encodedPassword));

        return issueTokens(result, refreshTokenService.issue(result.getId()));
    }

    public AuthResult login(String email, String password) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_EMAIL));

        if (!user.checkPassword(password, passwordEncoder)) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        return issueTokens(user, refreshTokenService.issue(user.getId()));
    }

    @Transactional
    public AuthResult refresh(String refreshToken) {
        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(refreshToken);
        User user = userRepository.findById(rotated.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        return issueTokens(user, rotated.refreshToken());
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private AuthResult issueTokens(User user, String refreshToken) {
        String accessToken = jwtUtil.generateToken(user.getId().toString());
        LoginResponse response = LoginResponse.of(accessToken, LoginResponse.UserInfo.of(user.getName(), user.getEmail()));
        return new AuthResult(response, refreshToken);
    }

}
