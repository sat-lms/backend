package com.sat.lms.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationSeconds;
    private final Clock clock;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-seconds:3600}") long expirationSeconds,
            Clock clock
    ) {
        this.key = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.expirationSeconds = expirationSeconds;
        this.clock = clock;
    }

    /**
     * Access Token 생성
     */
    public String createAccessToken(Long memberId, String role) {

        Instant now = clock.instant();

        return Jwts.builder()
                .subject(memberId.toString())
                .claim("memberId", memberId)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(
                        now.plusSeconds(expirationSeconds)
                ))
                .signWith(key)
                .compact();
    }

    /**
     * JWT 유효성 검사
     */
    public boolean validateToken(String token) {

        try {
            parseClaims(token);

            return true;

        } catch (JwtException | IllegalArgumentException e) {

            return false;
        }
    }

    /**
     * JWT에서 memberId 추출
     */
    public Long getMemberId(String token) {

        Claims claims = getClaims(token);

        return claims.get(
                "memberId",
                Long.class
        );
    }

    /**
     * JWT에서 role 추출
     */
    public String getRole(String token) {

        Claims claims = getClaims(token);

        return claims.get(
                "role",
                String.class
        );
    }

    /**
     * JWT Claims 조회
     */
    private Claims getClaims(String token) {
        return parseClaims(token);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .clock(() -> Date.from(clock.instant()))
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
