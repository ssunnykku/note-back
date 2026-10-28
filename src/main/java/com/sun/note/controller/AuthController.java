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

    // 로그인
    @PostMapping("login")
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest dto) {
        AuthResult result = userService.login(dto.email(), dto.password());
        return withRefreshCookie(HttpStatus.OK, result);
    }

    // 회원가입
    @PostMapping("signup")
    ResponseEntity<LoginResponse> signup(@Valid @RequestBody SignupRequest dto) {
        AuthResult result = userService.signUp(dto.name(), dto.email(), dto.password(), dto.passwordConfirm());
        return withRefreshCookie(HttpStatus.CREATED, result);
    }

    // 액세스 토큰 갱신. refresh 토큰도 새로 발급(회전)한다.
    // 실패(401)해도 쿠키를 지우지 않는다. 쿠키 저장소는 탭끼리 공유되므로, 다른 탭이 먼저 회전해 받은 새 쿠키를
    // 늦게 도착한 실패 응답이 지우면 두 탭 모두 로그아웃된다. 무효한 쿠키가 남아도 다음 refresh가 401을 받을 뿐이다.
    @PostMapping("refresh")
    ResponseEntity<LoginResponse> refresh(HttpServletRequest request) {
        return withRefreshCookie(HttpStatus.OK, userService.refresh(readRefreshCookie(request)));
    }

    // 로그아웃. refresh 토큰을 폐기하고 쿠키를 지운다. 쿠키가 없어도 204. (액세스 토큰은 만료까지 유효)
    @PostMapping("logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        userService.logout(readRefreshCookie(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                .build();
    }

    // @CookieValue는 값을 URL 디코딩하다 잘못된 % 시퀀스에서 예외(500)를 던지므로 원본 값을 그대로 읽는다.
    // 발급하는 토큰은 base64url이라 디코딩이 필요 없다.
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
