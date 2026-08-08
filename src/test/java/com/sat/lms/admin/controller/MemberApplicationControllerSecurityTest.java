package com.sat.lms.admin.controller;

import com.sat.lms.admin.dto.MemberReviewRequest;
import com.sat.lms.admin.service.MemberApplicationService;
import com.sat.lms.admin.service.MemberReviewService;
import com.sat.lms.global.config.SecurityConfig;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import com.sat.lms.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberApplicationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class MemberApplicationControllerSecurityTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean MemberApplicationService memberApplicationService;
    @MockitoBean MemberReviewService memberReviewService;
    @MockitoBean JwtTokenProvider tokenProvider;

    @Test
    void adminCanGetMemberApplications() throws Exception {
        authenticate("admin-token", 7L, "ADMIN");
        when(memberApplicationService.getMemberApplications(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/member-applications")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanApproveUsingAuthenticatedMemberId() throws Exception {
        authenticate("admin-token", 7L, "ADMIN");

        mockMvc.perform(patch("/api/v1/admin/member-applications/10")
                        .header("Authorization", "Bearer admin-token")
                        .contentType("application/json")
                        .content("{\"action\":\"APPROVED\"}"))
                .andExpect(status().isOk());

        verify(memberReviewService).review(eq(10L), any(MemberReviewRequest.class), eq(7L));
    }

    @Test
    void adminCanRejectUsingAuthenticatedMemberId() throws Exception {
        authenticate("admin-token", 7L, "ADMIN");

        mockMvc.perform(patch("/api/v1/admin/member-applications/10")
                        .header("Authorization", "Bearer admin-token")
                        .contentType("application/json")
                        .content("{\"action\":\"REJECTED\",\"rejectionReason\":\"조건 미충족\"}"))
                .andExpect(status().isOk());

        verify(memberReviewService).review(eq(10L), any(MemberReviewRequest.class), eq(7L));
    }

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/member-applications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentTokenReturnsForbidden() throws Exception {
        authenticate("student-token", 8L, "STUDENT");

        mockMvc.perform(get("/api/v1/admin/member-applications")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidOrExpiredTokenReturnsUnauthorized() throws Exception {
        when(tokenProvider.validateToken("invalid-token")).thenReturn(false);

        mockMvc.perform(get("/api/v1/admin/member-applications")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    private void authenticate(String token, Long memberId, String role) {
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getMemberId(token)).thenReturn(memberId);
        when(tokenProvider.getRole(token)).thenReturn(role);
    }
}
