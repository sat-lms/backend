package com.sat.lms.auth.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withPropertyValues(
                    "rate-limit.auth.login.capacity=10",
                    "rate-limit.auth.login.period=1m",
                    "rate-limit.auth.signup.capacity=5",
                    "rate-limit.auth.signup.period=1h",
                    "rate-limit.auth.cache.maximum-size=10000",
                    "rate-limit.auth.cache.expire-after-access=2h");

    @Test
    void validPositiveConfigurationStarts() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AuthRateLimitProperties.class).login().capacity()).isEqualTo(10);
        });
    }

    @Test
    void zeroCapacityFailsAtStartup() {
        runner.withPropertyValues("rate-limit.auth.login.capacity=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void nonPositiveDurationFailsAtStartup() {
        runner.withPropertyValues("rate-limit.auth.signup.period=0s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void malformedDurationFailsAtStartup() {
        runner.withPropertyValues("rate-limit.auth.login.period=not-a-duration")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthRateLimitProperties.class)
    static class PropertiesConfiguration {
    }
}
