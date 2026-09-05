package com.sat.lms.auth.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

import java.util.concurrent.TimeUnit;

public class AuthRateLimitStore {

    private final Cache<BucketKey, Bucket> buckets;
    private final RateLimitTimeSource timeSource;

    public AuthRateLimitStore(AuthRateLimitProperties properties, RateLimitTimeSource timeSource) {
        this.timeSource = timeSource;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(properties.cache().maximumSize())
                .expireAfterAccess(properties.cache().expireAfterAccess())
                .ticker(timeSource::currentTimeNanos)
                .build();
    }

    public Result consume(Endpoint endpoint, String clientIp, AuthRateLimitProperties.Limit limit) {
        Bucket bucket = buckets.get(new BucketKey(endpoint, clientIp), ignored -> newBucket(limit));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        long retryAfterSeconds = probe.isConsumed() ? 0
                : Math.max(1, ceilSeconds(probe.getNanosToWaitForRefill()));
        return new Result(probe.isConsumed(), limit.capacity(), probe.getRemainingTokens(), retryAfterSeconds);
    }

    public void clear() {
        buckets.invalidateAll();
        buckets.cleanUp();
    }

    long estimatedSize() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }

    private Bucket newBucket(AuthRateLimitProperties.Limit limit) {
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(limit.capacity())
                .refillIntervally(limit.capacity(), limit.period())
                .build();
        TimeMeter timeMeter = new TimeMeter() {
            @Override
            public long currentTimeNanos() {
                return timeSource.currentTimeNanos();
            }

            @Override
            public boolean isWallClockBased() {
                return false;
            }
        };
        return Bucket.builder().addLimit(bandwidth).withCustomTimePrecision(timeMeter).build();
    }

    private long ceilSeconds(long nanos) {
        long oneSecond = TimeUnit.SECONDS.toNanos(1);
        return nanos / oneSecond + (nanos % oneSecond == 0 ? 0 : 1);
    }

    public enum Endpoint { LOGIN, SIGNUP }

    private record BucketKey(Endpoint endpoint, String clientIp) { }

    public record Result(boolean allowed, long limit, long remaining, long retryAfterSeconds) { }
}
