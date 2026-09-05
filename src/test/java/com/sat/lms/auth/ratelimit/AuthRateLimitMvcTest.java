package com.sat.lms.auth.ratelimit;

import com.sat.lms.auth.controller.AuthController;
import com.sat.lms.auth.dto.LoginRequest;
import com.sat.lms.auth.dto.SignupRequest;
import com.sat.lms.auth.service.AuthService;
import com.sat.lms.global.config.SecurityConfig;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@org.springframework.context.annotation.Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
        "rate-limit.auth.login.capacity=10",
        "rate-limit.auth.login.period=1m",
        "rate-limit.auth.signup.capacity=5",
        "rate-limit.auth.signup.period=1h",
        "rate-limit.auth.cache.maximum-size=100",
        "rate-limit.auth.cache.expire-after-access=2h"
})
class AuthRateLimitMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthRateLimitStore store;
    @Autowired
    @Qualifier("authRateLimitFilterRegistration")
    FilterRegistrationBean<AuthRateLimitFilter> filterRegistration;
    @MockitoBean AuthService authService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;
    @MockitoBean MemberRepository memberRepository;
    @MockitoBean PasswordEncoder passwordEncoder;
    @MockitoSpyBean AuthController authController;

    @BeforeEach
    void clearBuckets() {
        store.clear();
    }

    @Test
    void servletContainerRegistrationIsDisabledAndSecurityChainRunsFilterOnce() throws Exception {
        assertThat(filterRegistration.isEnabled()).isFalse();
        mockMvc.perform(login("192.0.2.50"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "9"));
        verify(authController).login(any(LoginRequest.class));
    }

    @Test
    void loginAllowsTenAndRejectsEleventhBeforeControllerExactlyOncePerRequest() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(login("192.0.2.1"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", "10"))
                    .andExpect(header().string("X-RateLimit-Remaining", Integer.toString(9 - i)));
        }
        verify(authController, times(10)).login(any(LoginRequest.class));
        verify(authService, times(10)).login(any(LoginRequest.class));
        clearInvocations(authController, authService, memberRepository, passwordEncoder, jwtTokenProvider);

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(remote("192.0.2.1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{malformed-json"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().encoding("UTF-8"))
                .andExpect(header().longValue("Retry-After", 60))
                .andExpect(header().string("X-RateLimit-Limit", "10"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(content().json("""
                        {"success":false,"message":"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.","data":null}
                        """, JsonCompareMode.STRICT))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("요청이 너무 많습니다. 잠시 후 다시 시도해주세요."))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(authController, never()).login(any());
        verify(authService, never()).login(any());
        verifyNoInteractions(memberRepository, passwordEncoder, jwtTokenProvider);
    }

    @Test
    void signupAllowsFiveAndRejectsSixth() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(signup("192.0.2.2")).andExpect(status().isCreated());
        }
        mockMvc.perform(signup("192.0.2.2"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().longValue("Retry-After", 3600));
        verify(authService, times(5)).signup(any(SignupRequest.class));
    }

    @Test
    void differentIpsAndEndpointsUseIndependentBuckets() throws Exception {
        mockMvc.perform(login("192.0.2.10"))
                .andExpect(header().string("X-RateLimit-Remaining", "9"));
        mockMvc.perform(login("192.0.2.11"))
                .andExpect(header().string("X-RateLimit-Remaining", "9"));
        mockMvc.perform(signup("192.0.2.10"))
                .andExpect(header().string("X-RateLimit-Limit", "5"))
                .andExpect(header().string("X-RateLimit-Remaining", "4"));
        mockMvc.perform(login("192.0.2.10"))
                .andExpect(header().string("X-RateLimit-Remaining", "8"));
    }

    @Test
    void untrustedForwardedHeadersCannotBypassRemoteAddressLimit() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(login("198.51.100.1").header("X-Forwarded-For", "203.0.113." + i))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(login("198.51.100.1").header("X-Forwarded-For", "203.0.113.200"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void optionsLogoutOtherPathsAndOtherMethodsAreNotLimited() throws Exception {
        for (int i = 0; i < 12; i++) {
            mockMvc.perform(options("/api/v1/auth/login").with(remote("203.0.113.1")))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
            mockMvc.perform(post("/api/v1/auth/logout").with(remote("203.0.113.1")))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/auth/login").with(remote("203.0.113.1")))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
            mockMvc.perform(get("/api/v1/members/me").with(remote("203.0.113.1")))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
        }
        mockMvc.perform(login("203.0.113.1"))
                .andExpect(header().string("X-RateLimit-Remaining", "9"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String ip) {
        return post("/api/v1/auth/login")
                .with(remote(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentNumber\":\"20231234\",\"password\":\"abc12345\"}");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signup(String ip) {
        return post("/api/v1/auth/signup")
                .with(remote(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentNumber\":\"20231234\",\"name\":\"학생\","
                        + "\"password\":\"abc12345\",\"passwordConfirm\":\"abc12345\"}");
    }

    private RequestPostProcessor remote(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
