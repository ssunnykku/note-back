package com.sun.note.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

public class JwtUtil {

    private final SecretKey key;
    private final long accessExpired;

    public JwtUtil(String secret, long accessExpired) {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessExpired = accessExpired;
    }

    // 토큰 생성
    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username) //  setSubject → subject
                .issuedAt(new Date(System.currentTimeMillis())) // setIssuedAt → issuedAt
                .expiration(new Date(System.currentTimeMillis() + accessExpired)) // setExpiration → expiration
                .signWith(key)
                .compact();
    }

    // 토큰 검증
    public String validateToken(String token) {
        try {
            return Jwts.parser() // parserBuilder() → parser()
                    .verifyWith(key) // setSigningKey() → verifyWith()
                    .build()
                    .parseSignedClaims(token) // parseClaimsJws() → parseSignedClaims()
                    .getPayload()
                    .getSubject();
        } catch (Exception e) {
            return null;
        }
    }
    
}
