package com.sun.note.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.sun.note.util.JwtUtil;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtFilter extends OncePerRequestFilter {

    // 만료된 액세스 토큰으로 요청했음을 JwtAuthenticationEntryPoint에 알리는 요청 속성
    public static final String EXPIRED_ATTRIBUTE = JwtFilter.class.getName() + ".expired";

    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            String username = toUserId(jwtUtil.validateToken(token));

            if (username == null && jwtUtil.isExpired(token)) {
                request.setAttribute(EXPIRED_ATTRIBUTE, true);
            }

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(username, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }

    // 컨트롤러가 인증 이름을 UUID로 변환하므로, UUID가 아닌 subject는 인증하지 않는다. (변환 실패 500 방지)
    private static String toUserId(String subject) {
        if (subject == null) {
            return null;
        }
        try {
            UUID.fromString(subject);
            return subject;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
