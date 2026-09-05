package com.sat.lms.auth.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuthRateLimitProperties.class)
public class AuthRateLimitConfig {

    @Bean
    RateLimitTimeSource rateLimitTimeSource() {
        return System::nanoTime;
    }

    @Bean
    ClientIpResolver clientIpResolver() {
        return new ClientIpResolver();
    }

    @Bean
    AuthRateLimitStore authRateLimitStore(AuthRateLimitProperties properties,
                                          RateLimitTimeSource timeSource) {
        return new AuthRateLimitStore(properties, timeSource);
    }

    @Bean
    AuthRateLimitFilter authRateLimitFilter(AuthRateLimitProperties properties,
                                            AuthRateLimitStore store,
                                            ClientIpResolver clientIpResolver,
                                            ObjectMapper objectMapper) {
        return new AuthRateLimitFilter(properties, store, clientIpResolver, objectMapper);
    }

    @Bean
    FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilterRegistration(AuthRateLimitFilter filter) {
        FilterRegistrationBean<AuthRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
