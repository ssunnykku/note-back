package com.sun.note.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sun.note.dto.LoginRequest;
import com.sun.note.dto.LoginResponse;
import com.sun.note.dto.SignupRequest;
import com.sun.note.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;

    // 로그인
    @PostMapping("login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest dto) {
        LoginResponse response = userService.login(dto.email(), dto.password());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    // 회원가입
    @PostMapping("signup")
    ResponseEntity<LoginResponse> signup(@Valid @RequestBody SignupRequest dto) {
        LoginResponse response = userService.signUp(dto.name(), dto.email(), dto.password(), dto.passwordConfirm());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

}
