package com.sat.lms.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.sat.lms.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenProviderTest {
    private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes-long";
    private static final Instant ISSUED_AT = Instant.parse("2026-08-30T00:00:00Z");

    @Test
    void claimsAndExpirationComeFromFixedClock() {
        Clock clock = Clock.fixed(ISSUED_AT, ZoneOffset.UTC);
        JwtTokenProvider provider = provider(SECRET, 3600, clock, 7L);
        String token = provider.createAccessToken(2L, "STUDENT", 7L);
        Claims claims = parse(token, clock);

        assertThat(claims.getSubject()).isEqualTo("2");
        assertThat(claims.get("memberId", Long.class)).isEqualTo(2L);
        assertThat(claims.get("role", String.class)).isEqualTo("STUDENT");
        assertThat(((Number) claims.get("tokenVersion")).longValue()).isEqualTo(7L);
        assertThat(claims.getIssuedAt()).isEqualTo(Date.from(ISSUED_AT));
        assertThat(claims.getExpiration()).isEqualTo(Date.from(ISSUED_AT.plusSeconds(3600)));
        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getMemberId(token)).isEqualTo(2L);
        assertThat(provider.getRole(token)).isEqualTo("STUDENT");
    }

    @Test
    void validationUsesInjectedClockBeforeAtAndAfterExpiration() {
        MutableClock clock = new MutableClock(ISSUED_AT);
        JwtTokenProvider provider = provider(SECRET, 60, clock, 0L);
        String token = provider.createAccessToken(2L, "STUDENT");

        clock.setInstant(ISSUED_AT.plusSeconds(59));
        assertThat(provider.validateToken(token)).isTrue();
        clock.setInstant(ISSUED_AT.plusSeconds(60));
        assertThat(provider.validateToken(token)).isTrue();
        clock.setInstant(ISSUED_AT.plusSeconds(61));
        assertThat(provider.validateToken(token)).isFalse();
    }

    @Test
    void rejectsDifferentSignatureAndTamperedToken() {
        Clock clock = Clock.fixed(ISSUED_AT, ZoneOffset.UTC);
        String token = provider(SECRET, 3600, clock, 0L).createAccessToken(2L, "STUDENT");
        JwtTokenProvider other = provider(
                "different-secret-key-must-be-at-least-32-bytes", 3600, clock, 0L);

        assertThat(other.validateToken(token)).isFalse();
        String[] parts = token.split("\\.");
        char replacement = parts[1].charAt(0) == 'a' ? 'b' : 'a';
        String tampered = parts[0] + "." + replacement + parts[1].substring(1) + "." + parts[2];
        assertThat(provider(SECRET, 3600, clock, 0L).validateToken(tampered)).isFalse();
    }

    @Test
    void rejectsMissingOrNonCurrentTokenVersion() {
        Clock clock = Clock.fixed(ISSUED_AT, ZoneOffset.UTC);
        JwtTokenProvider provider = provider(SECRET, 3600, clock, 3L);
        String staleToken = provider.createAccessToken(2L, "STUDENT", 2L);
        String tokenWithoutVersion = Jwts.builder()
                .subject("2")
                .claim("memberId", 2L)
                .claim("role", "STUDENT")
                .issuedAt(Date.from(ISSUED_AT))
                .expiration(Date.from(ISSUED_AT.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(provider.validateToken(staleToken)).isFalse();
        assertThat(provider.validateToken(tokenWithoutVersion)).isFalse();
    }

    private JwtTokenProvider provider(String secret, long expirationSeconds, Clock clock, long tokenVersion) {
        MemberRepository memberRepository = mock(MemberRepository.class);
        when(memberRepository.findTokenVersionById(2L)).thenReturn(Optional.of(tokenVersion));
        return new JwtTokenProvider(secret, expirationSeconds, clock, memberRepository);
    }

    private Claims parse(String token, Clock clock) {
        return Jwts.parser()
                .clock(() -> Date.from(clock.instant()))
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
