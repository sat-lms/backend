package com.sat.lms.global.security;

import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.StandardEnvironment;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSecretConfigurationTest {

    private static final String TEST_SECRET = "test-secret-key-must-be-at-least-32-bytes";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> {
                context.getEnvironment().getPropertySources()
                        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                context.getEnvironment().getPropertySources()
                        .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            })
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withPropertyValues("jwt.secret=${JWT_SECRET}")
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void failsToStartWhenJwtSecretIsMissing() {
        contextRunner.run(context -> assertThat(context.getStartupFailure())
                .hasRootCauseInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void failsToStartWhenJwtSecretIsEmpty() {
        contextRunner.withPropertyValues("JWT_SECRET=")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(WeakKeyException.class));
    }

    @Test
    void startsWhenJwtSecretIsValid() {
        contextRunner.withPropertyValues("JWT_SECRET=" + TEST_SECRET)
                .run(context -> assertThat(context).hasSingleBean(JwtTokenProvider.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(JwtTokenProvider.class)
    static class TestConfiguration {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
