package com.sat.lms.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sat.lms.global.response.ApiResponse;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
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
                        // 공개 엔드포인트는 여기에 명시적으로만 나열한다. 기본값이 authenticated()로
                        // 바뀌었으므로, 새 API를 추가했는데 규칙을 깜빡해도 "조용한 공개"가 아니라
                        // 즉시 401로 드러난다(#86).
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/signup").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        // logout은 무상태 JWT라 서버가 실질적으로 아무 상태도 바꾸지 않는 완전한
                        // no-op이다(AuthController.logout() 참고). 인증을 요구해도 잃을 게 없지만,
                        // 만료된 토큰으로도 "로그아웃"은 성공해야 한다는 관점에서 공개로 유지한다.
                        // 나중에 토큰 블랙리스트 등 실제 상태 변경이 추가되면 이 줄부터 재검토할 것.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()
                        // deploy.yml의 배포 후 헬스체크가 /v3/api-docs를 호출한다 — 여기서 빠지면
                        // 다음 배포부터 CD가 실패한다.
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // /api/v1/admin/** 전체에 대한 포괄 규칙. 개별 admin 경로를 나열하는 방식은
                        // 새 admin API를 추가할 때 규칙 추가를 깜빡하면 기본값(authenticated())으로만
                        // 열려서 ADMIN 전용이어야 할 API가 일반 인증 사용자에게 노출될 수 있었다.
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // {noticeId}/{attachmentId} 등은 리소스 ID로 항상 정확히 한 세그먼트이고
                        // 그 뒤에 고정 리터럴이 이어지므로 *를 유지한다(**로 바꾸면 오히려
                        // 의도보다 넓게 매칭됨).
                        .requestMatchers(HttpMethod.POST, "/api/v1/notices/*/attachments").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/notice-attachments/*/download-url").authenticated()
                        // 아래는 경로가 ID에서 끝나(뒤에 아무 것도 없어) 나중에 이 ID 아래로 중첩
                        // 경로가 추가되면 이 규칙이 안 걸리고 기본값으로 흘러갈 수 있었다(#86의
                        // 핵심 문제 사례: PATCH /assignments/*가 PATCH /assignments/*/status를
                        // 못 잡던 것과 동일 패턴) — **로 바꿔 하위 경로 추가에도 견고하게 만든다.
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/notice-attachments/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/notices").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/notices/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/notices/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/notices/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/assignments/*/submission").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/assignments/*/submission").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/assignments/*/submission").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/assignments/*/submission").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/assignments/*/attachments").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/assignment-attachments/*/download-url")
                        .authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/assignment-attachments/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/assignments").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/assignments/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/assignments/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/assignments/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/submission-attachments/*/download-url")
                        .authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/submission-attachments/**").authenticated()
                        .requestMatchers("/api/v1/members/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/members/me/submissions").authenticated()
                        .anyRequest().authenticated())
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
