package com.sun.note.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

import com.sun.note.config.AuthProperties;
import com.sun.note.dto.LoginRequest;
import com.sun.note.dto.LoginResponse;
import com.sun.note.dto.SignupRequest;
import com.sun.note.service.UserService;
import com.sun.note.service.UserService.AuthResult;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    // refresh 토큰 쿠키. JS에서 읽을 수 없고(HttpOnly), /api/auth 요청에만 실려 간다.
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final UserService userService;
    private final AuthProperties authProperties;

    @PostMapping("login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest dto) {
        AuthResult result = userService.login(dto.email(), dto.password());
        return withRefreshCookie(HttpStatus.OK, result);
    }

    @PostMapping("signup")
    ResponseEntity<LoginResponse> signup(@Valid @RequestBody SignupRequest dto) {
        AuthResult result = userService.signUp(dto.name(), dto.email(), dto.password(), dto.passwordConfirm());
        return withRefreshCookie(HttpStatus.CREATED, result);
    }

    // 실패해도 쿠키를 지우지 않는다. 늦게 온 실패 응답이 다른 탭이 막 받은 새 쿠키를 지워 모든 탭이 로그아웃되기 때문
    @PostMapping("refresh")
    ResponseEntity<LoginResponse> refresh(HttpServletRequest request) {
        return withRefreshCookie(HttpStatus.OK, userService.refresh(readRefreshCookie(request)));
    }

    @PostMapping("logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        userService.logout(readRefreshCookie(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                .build();
    }

    // @CookieValue는 잘못된 % 인코딩에서 500을 내므로 원본을 읽는다 (토큰은 base64url이라 디코딩 불필요)
    private static String readRefreshCookie(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, REFRESH_COOKIE);
        return cookie == null ? null : cookie.getValue();
    }

    private ResponseEntity<LoginResponse> withRefreshCookie(HttpStatus status, AuthResult result) {
        ResponseCookie cookie = refreshCookie(result.refreshToken())
                .maxAge(authProperties.refreshToken().ttl())
                .build();
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.response());
    }

    private ResponseCookie expiredRefreshCookie() {
        return refreshCookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder refreshCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(authProperties.refreshToken().cookieSecure())
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH);
    }

}
