package com.sat.lms.global.config;

import com.sat.lms.auth.controller.AuthController;
import com.sat.lms.auth.service.AuthService;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import com.sat.lms.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SecurityConfig의 기본 정책(#86: anyRequest().authenticated())을 검증한다.
 * 시큐리티 필터 체인은 DispatcherServlet의 핸들러 매핑보다 먼저 요청을 평가하므로,
 * 어떤 컨트롤러가 로드된 슬라이스에서든 "매핑도, 규칙도 없는 경로"의 차단 여부를
 * 동일하게 검증할 수 있다.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SecurityConfigTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AuthService authService;
    @MockitoBean JwtTokenProvider tokenProvider;

    @Test
    void unmappedPathWithoutExplicitRuleRequiresAuthenticationByDefault() throws Exception {
        // 어떤 컨트롤러에도 매핑되지 않고 SecurityConfig에도 규칙이 없는 경로.
        // 이전(anyRequest().permitAll())에는 필터를 그냥 통과해 DispatcherServlet까지
        // 가서 404/500이 났지만, 이제는 시큐리티 필터에서 401로 먼저 막혀야 한다.
        mockMvc.perform(get("/api/v1/some-random-path"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
    }

    @Test
    void unmappedPathWithInvalidTokenIsAlsoRejected() throws Exception {
        mockMvc.perform(get("/api/v1/some-random-path").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }
}
