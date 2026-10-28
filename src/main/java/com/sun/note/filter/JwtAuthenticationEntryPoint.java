package com.sun.note.filter;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.note.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 인증이 필요한 요청에 유효한 액세스 토큰이 없을 때 401 ProblemDetail로 응답한다.
// 만료된 토큰이면 TOKEN_EXPIRED(프론트가 refresh 후 재시도), 그 외(없음/위조/형식 오류)는 UNAUTHORIZED.
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode code = Boolean.TRUE.equals(request.getAttribute(JwtFilter.EXPIRED_ATTRIBUTE))
                ? ErrorCode.TOKEN_EXPIRED
                : ErrorCode.UNAUTHORIZED;

        ProblemDetail problem = ProblemDetail.forStatus(code.getStatus());
        problem.setTitle(code.name());
        problem.setDetail(code.getMessage());
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
