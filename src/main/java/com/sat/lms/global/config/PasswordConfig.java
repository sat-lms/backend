package com.sat.lms.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Qualifier("loginDummyPasswordHash")
    public String loginDummyPasswordHash(PasswordEncoder passwordEncoder) {
        return passwordEncoder.encode("login-timing-mitigation-dummy-password");
    }
}
