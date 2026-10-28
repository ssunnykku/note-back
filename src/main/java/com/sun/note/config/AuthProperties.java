package com.sun.note.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 인증 설정 (auth.*)
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(@DefaultValue RefreshToken refreshToken) {

    // ttl: 마지막 갱신 후 이 기간 동안 사용하지 않으면 다시 로그인해야 한다.
    // cookieSecure: true면 HTTPS에서만 쿠키를 보낸다. HTTPS가 없는 local/dev에서만 false로 둔다.
    public record RefreshToken(
            @DefaultValue("7d") Duration ttl,
            @DefaultValue("true") boolean cookieSecure) {
    }
}
