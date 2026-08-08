package com.sat.lms.global.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {
    private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes-long";

    @Test
    void createsAndValidatesTokenWithMemberAndRole() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 3600);
        String token = provider.createAccessToken(2L, "STUDENT");

        assertThat(provider.parse(token).getSubject()).isEqualTo("2");
        assertThat(provider.parse(token).get("memberId", Long.class)).isEqualTo(2L);
        assertThat(provider.parse(token).get("role", String.class)).isEqualTo("STUDENT");
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        String token = new JwtTokenProvider(SECRET, 3600).createAccessToken(2L, "STUDENT");
        JwtTokenProvider other = new JwtTokenProvider("different-secret-key-must-be-at-least-32-bytes", 3600);

        assertThatThrownBy(() -> other.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, -1);
        String token = provider.createAccessToken(2L, "STUDENT");

        assertThatThrownBy(() -> provider.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }
}
