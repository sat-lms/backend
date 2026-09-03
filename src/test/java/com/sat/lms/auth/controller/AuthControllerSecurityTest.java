package com.sat.lms.auth.controller;

import com.sat.lms.auth.dto.LoginRequest;
import com.sat.lms.auth.dto.LoginResponse;
import com.sat.lms.auth.dto.SignupRequest;
import com.sat.lms.auth.dto.SignupResponse;
import com.sat.lms.auth.service.AuthService;
import com.sat.lms.global.config.SecurityConfig;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import com.sat.lms.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AuthControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AuthService authService;
    @MockitoBean JwtTokenProvider tokenProvider;

    @Test
    void signupSucceedsWithoutAuthentication() throws Exception {
        SignupResponse response = new SignupResponse(1L, "20231234", "최인준", "PENDING", OffsetDateTime.now());
        when(authService.signup(any(SignupRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentNumber":"20231234","name":"최인준","password":"abc12345","passwordConfirm":"abc12345"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.studentNumber").value("20231234"));
    }

    @Test
    void loginSucceedsWithoutAuthentication() throws Exception {
        LoginResponse response = new LoginResponse(1L, "20231234", "최인준", "STUDENT", "APPROVED",
                "token", "Bearer", 3600);
        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentNumber":"20231234","password":"abc12345"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("token"));
    }

    @Test
    void logoutSucceedsWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void oldUnversionedLogoutPathNoLongerExists() throws Exception {
        // #86 이전에는 이 경로가 매핑도, 시큐리티 규칙도 없어 permitAll을 타고
        // DispatcherServlet까지 가서 404가 났다. 이제는 anyRequest().authenticated()
        // 기본값에 걸려 시큐리티 필터 단계에서 401로 먼저 막힌다(핸들러 탐색까지
        // 가지도 못하므로 결과적으로 경로가 없다는 사실은 여전히 드러나지 않는다).
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
    }
}
