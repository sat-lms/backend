package com.sat.lms.auth.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitStoreTest {

    @Test
    void loginBoundaryRetryAfterAndRefillUseControlledTime() {
        MutableTimeSource time = new MutableTimeSource();
        AuthRateLimitStore store = store(time, 100, Duration.ofHours(2));
        AuthRateLimitProperties.Limit login = new AuthRateLimitProperties.Limit(10, Duration.ofMinutes(1));

        for (int i = 0; i < 10; i++) {
            assertThat(store.consume(AuthRateLimitStore.Endpoint.LOGIN, "192.0.2.1", login).allowed()).isTrue();
        }
        AuthRateLimitStore.Result blocked = store.consume(AuthRateLimitStore.Endpoint.LOGIN, "192.0.2.1", login);
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isEqualTo(60);

        time.advance(Duration.ofSeconds(59));
        assertThat(store.consume(AuthRateLimitStore.Endpoint.LOGIN, "192.0.2.1", login).retryAfterSeconds())
                .isEqualTo(1);
        time.advance(Duration.ofSeconds(1));
        assertThat(store.consume(AuthRateLimitStore.Endpoint.LOGIN, "192.0.2.1", login).allowed()).isTrue();
    }

    @Test
    void loginAndSignupUseIndependentKeys() {
        MutableTimeSource time = new MutableTimeSource();
        AuthRateLimitStore store = store(time, 100, Duration.ofHours(2));
        AuthRateLimitProperties.Limit login = new AuthRateLimitProperties.Limit(1, Duration.ofMinutes(1));
        AuthRateLimitProperties.Limit signup = new AuthRateLimitProperties.Limit(1, Duration.ofHours(1));

        assertThat(store.consume(AuthRateLimitStore.Endpoint.LOGIN, "192.0.2.1", login).allowed()).isTrue();
        assertThat(store.consume(AuthRateLimitStore.Endpoint.LOGIN, "192.0.2.1", login).allowed()).isFalse();
        assertThat(store.consume(AuthRateLimitStore.Endpoint.SIGNUP, "192.0.2.1", signup).allowed()).isTrue();
    }

    @Test
    void cacheMaximumSizeAndExpirationAreEnforcedAfterDeterministicCleanup() {
        MutableTimeSource time = new MutableTimeSource();
        AuthRateLimitStore store = store(time, 3, Duration.ofMinutes(5));
        AuthRateLimitProperties.Limit limit = new AuthRateLimitProperties.Limit(1, Duration.ofHours(1));
        for (int i = 0; i < 20; i++) {
            store.consume(AuthRateLimitStore.Endpoint.LOGIN, "192.0.2." + i, limit);
        }
        assertThat(store.estimatedSize()).isLessThanOrEqualTo(3);

        time.advance(Duration.ofMinutes(6));
        assertThat(store.estimatedSize()).isZero();
    }

    @Test
    void concurrentRequestsForSameIpNeverExceedCapacity() throws Exception {
        MutableTimeSource time = new MutableTimeSource();
        AuthRateLimitStore store = store(time, 100, Duration.ofHours(2));
        AuthRateLimitProperties.Limit limit = new AuthRateLimitProperties.Limit(10, Duration.ofMinutes(1));
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch ready = new CountDownLatch(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 20; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("start timeout");
                    }
                    return store.consume(AuthRateLimitStore.Endpoint.LOGIN, "2001:db8::1", limit).allowed();
                }));
            }
            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            long allowed = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(3, TimeUnit.SECONDS)) allowed++;
            }
            assertThat(allowed).isEqualTo(10);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }

    private AuthRateLimitStore store(MutableTimeSource time, long maximumSize, Duration expiration) {
        return new AuthRateLimitStore(new AuthRateLimitProperties(
                new AuthRateLimitProperties.Limit(10, Duration.ofMinutes(1)),
                new AuthRateLimitProperties.Limit(5, Duration.ofHours(1)),
                new AuthRateLimitProperties.Cache(maximumSize, expiration)), time);
    }

    private static final class MutableTimeSource implements RateLimitTimeSource {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long currentTimeNanos() {
            return nanos.get();
        }

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }
    }
}
