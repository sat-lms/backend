package com.sat.lms.admin.controller;

import com.sat.lms.admin.service.AdminMemberService;
import com.sat.lms.global.config.SecurityConfig;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import com.sat.lms.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMemberController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminMemberControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AdminMemberService service;
    @MockitoBean JwtTokenProvider tokens;

    @Test
    void approvedAdminExpelsMemberUsingJwtPrincipal() throws Exception {
        token("admin-token", 1L, "ADMIN");
        mockMvc.perform(delete("/api/v1/admin/members/{memberId}", 2L)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().encoding("UTF-8"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("회원을 추방했습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(service).expel(1L, 2L);
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/members/2"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void studentIsRejectedBySecurityBeforeService() throws Exception {
        token("student-token", 2L, "STUDENT");
        mockMvc.perform(delete("/api/v1/admin/members/3")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void inactiveAdminIsRejectedByServiceInCommonApiFormat() throws Exception {
        token("admin-token", 1L, "ADMIN");
        doThrow(new BusinessException(HttpStatus.FORBIDDEN, "탈퇴하거나 정지된 계정입니다."))
                .when(service).expel(1L, 2L);
        mockMvc.perform(delete("/api/v1/admin/members/2")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("탈퇴하거나 정지된 계정입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void missingTargetUsesExistingNotFoundContract() throws Exception {
        token("admin-token", 1L, "ADMIN");
        doThrow(new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."))
                .when(service).expel(1L, 999L);
        mockMvc.perform(delete("/api/v1/admin/members/999")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("존재하지 않는 회원입니다."));
    }

    @Test
    void malformedTargetIdIsBadRequest() throws Exception {
        token("admin-token", 1L, "ADMIN");
        mockMvc.perform(delete("/api/v1/admin/members/not-a-number")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(service);
    }

    private void token(String value, Long memberId, String role) {
        when(tokens.validateToken(value)).thenReturn(true);
        when(tokens.getMemberId(value)).thenReturn(memberId);
        when(tokens.getRole(value)).thenReturn(role);
    }
}
