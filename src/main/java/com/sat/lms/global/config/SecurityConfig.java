package com.sat.lms.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sat.lms.global.response.ApiResponse;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                            ObjectMapper objectMapper) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/notices").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/notices/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/notices/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/notices/**").authenticated()
                        .requestMatchers("/api/v1/admin/member-applications/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/members/me").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    objectMapper.writeValue(response.getWriter(), ApiResponse.fail("인증이 필요합니다."));
                }).accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    objectMapper.writeValue(response.getWriter(), ApiResponse.fail("접근 권한이 없습니다."));
                }))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class).build();
    }
}
