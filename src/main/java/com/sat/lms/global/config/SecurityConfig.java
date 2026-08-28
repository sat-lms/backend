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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "https://satlms.vercel.app"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                            ObjectMapper objectMapper,
                                            CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/notices/*/attachments").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/notice-attachments/*/download-url").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/notice-attachments/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/notices").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/notices/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/notices/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/notices/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/assignments/*/submission").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/assignments/*/submission").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/assignments/*/submission").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/assignments/*/submission").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/assignments").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/assignments/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/assignments/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/assignments/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/submission-attachments/*/download-url")
                        .authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/submission-attachments/*").authenticated()
                        .requestMatchers("/api/v1/admin/member-applications/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/assignments/*/submissions")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/submissions/*").hasRole("ADMIN")
                        .requestMatchers("/api/v1/members/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/members/me/submissions").authenticated()
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
