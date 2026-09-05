package com.sat.lms.auth.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "rate-limit.auth")
public record AuthRateLimitProperties(
        @Valid @NotNull Limit login,
        @Valid @NotNull Limit signup,
        @Valid @NotNull Cache cache
) {
    public record Limit(@Positive long capacity, @NotNull Duration period) {
        public Limit {
            if (period != null && (period.isZero() || period.isNegative())) {
                throw new IllegalArgumentException("rate limit period must be positive");
            }
        }
    }

    public record Cache(@Positive long maximumSize, @NotNull Duration expireAfterAccess) {
        public Cache {
            if (expireAfterAccess != null && (expireAfterAccess.isZero() || expireAfterAccess.isNegative())) {
                throw new IllegalArgumentException("rate limit cache expiration must be positive");
            }
        }
    }
}
