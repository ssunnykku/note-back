package com.sun.note.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.sun.note.util.JwtUtil;

@Configuration
public class JwtConfig {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expired}")
    private long accessExpired;
  
    @Bean
    public JwtUtil jwtUtil() {
        return new JwtUtil(secret, accessExpired);
    }
}
