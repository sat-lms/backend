package com.sat.lms.member.controller;

import com.sat.lms.global.config.SecurityConfig;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.member.service.MemberService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class MemberControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean MemberService memberService;
    @MockitoBean JwtTokenProvider tokenProvider;

    @Test
    void validJwtUsesMemberIdFromToken() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("2");
        when(claims.get("role", String.class)).thenReturn("STUDENT");
        when(tokenProvider.parse("valid-token")).thenReturn(claims);

        mockMvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());

        verify(memberService).getMe(2L);
    }

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void invalidTokenReturnsUnauthorized() throws Exception {
        when(tokenProvider.parse("invalid-token")).thenThrow(new IllegalArgumentException("invalid"));

        mockMvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
