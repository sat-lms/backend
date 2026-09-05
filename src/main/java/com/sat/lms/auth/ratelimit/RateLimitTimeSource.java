package com.sat.lms.auth.ratelimit;

@FunctionalInterface
public interface RateLimitTimeSource {
    long currentTimeNanos();
}
