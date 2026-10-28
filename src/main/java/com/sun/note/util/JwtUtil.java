package com.sun.note.util;

import io.jsonwebtoken.ExpiredJwtException;
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

    // 서명은 올바르지만 만료된 토큰인지. 401 응답에서 "만료"(갱신 후 재시도 가능)와 "위조/형식 오류"를 구분하는 데 쓴다.
    // jjwt는 서명 검증 후 만료를 확인하므로 위조 토큰은 ExpiredJwtException이 나지 않는다.
    public boolean isExpired(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
