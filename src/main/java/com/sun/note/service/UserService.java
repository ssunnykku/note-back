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

    @Transactional
    public LoginResponse signUp(String name, String email, String password, String passwordConfirm) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        if (!password.equals(passwordConfirm)) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }
        String encodedPassword = passwordEncoder.encode(password);

        User result = userRepository.save(User.of(name, email, encodedPassword));
        String accessToken = jwtUtil.generateToken(result.getId().toString());

        return LoginResponse.of(accessToken, LoginResponse.UserInfo.of(result.getName(), result.getEmail()));
    }

    public LoginResponse login(String email, String password) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_EMAIL));

        if (!user.checkPassword(password, passwordEncoder)) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        String accessToken = jwtUtil.generateToken(user.getId().toString());
        return LoginResponse.of(accessToken, LoginResponse.UserInfo.of(user.getName(), user.getEmail()));
    }

}
