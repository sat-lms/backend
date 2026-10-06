package com.sat.lms.admin.controller;

import com.sat.lms.admin.dto.MemberReviewRequest;
import com.sat.lms.admin.service.MemberApplicationService;
import com.sat.lms.admin.service.MemberReviewService;
import com.sat.lms.global.config.SecurityConfig;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.global.exception.BusinessException;
import com.fasterxml.jackson.core.exc.InputCoercionException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.json.JsonCompareMode;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Collections;
import com.sat.lms.admin.dto.BulkMemberApprovalRequest;
import com.sat.lms.admin.dto.BulkMemberApprovalResponse;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

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
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().encoding("UTF-8"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."));
    }

    @Test
    void missingReviewActionReturnsBadRequest() throws Exception {
        authenticate("admin-token", 7L, "ADMIN");

        mockMvc.perform(patch("/api/v1/admin/member-applications/10")
                        .header("Authorization", "Bearer admin-token")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("action은 필수입니다."));
    }

    @Test
    void invalidJsonEnumReturnsBadRequest() throws Exception {
        authenticate("admin-token", 7L, "ADMIN");

        mockMvc.perform(patch("/api/v1/admin/member-applications/10")
                        .header("Authorization", "Bearer admin-token")
                        .contentType("application/json")
                        .content("{\"action\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));
    }

    @Test
    void invalidPathVariableReturnsBadRequest() throws Exception {
        authenticate("admin-token", 7L, "ADMIN");

        mockMvc.perform(patch("/api/v1/admin/member-applications/not-a-number")
                        .header("Authorization", "Bearer admin-token")
                        .contentType("application/json")
                        .content("{\"action\":\"APPROVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void invalidQueryParameterReturnsBadRequest() throws Exception {
        authenticate("admin-token", 7L, "ADMIN");

        mockMvc.perform(get("/api/v1/admin/member-applications?status=UNKNOWN")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void businessExceptionKeepsStatusAndMessage() throws Exception {
        authenticate("admin-token", 7L, "ADMIN");
        when(memberReviewService.review(eq(10L), any(MemberReviewRequest.class), eq(7L)))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."));

        mockMvc.perform(patch("/api/v1/admin/member-applications/10")
                        .header("Authorization", "Bearer admin-token")
                        .contentType("application/json")
                        .content("{\"action\":\"APPROVED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 회원입니다."));
    }

    @Test
    void unexpectedExceptionReturnsSafeInternalServerError() throws Exception {
        authenticate("admin-token", 7L, "ADMIN");
        when(memberApplicationService.getMemberApplications(any(), any()))
                .thenThrow(new RuntimeException("database password leaked"));

        mockMvc.perform(get("/api/v1/admin/member-applications")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("database password leaked"))));
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

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "{}", "null", "{\"memberIds\":null}", "{\"memberIds\":[]}",
            "{\"memberIds\":[null]}", "{\"memberIds\":[0]}", "{\"memberIds\":[-1]}",
            "{\"memberIds\":[1.2]}", "{\"memberIds\":[\"12\"]}", "{\"memberIds\":[true]}",
            "{\"memberIds\":[{}]}", "{\"memberIds\":12}", "{\"memberIds\":[9223372036854775808]}"})
    void invalidBulkInputUsesCommon400(String body) throws Exception {
        authenticate("admin-token",7L,"ADMIN");
        mockMvc.perform(post("/api/v1/admin/member-applications/bulk-approve")
                .header("Authorization","Bearer admin-token").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(memberReviewService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"9223372036854775808", "99999999999999999999", "-9223372036854775809"})
    void outOfRangeMemberIdsUseCommon400WithoutExposingInternalExceptions(String id) throws Exception {
        authenticate("admin-token", 7L, "ADMIN");
        var result = mockMvc.perform(post("/api/v1/admin/member-applications/bulk-approve")
                        .header("Authorization", "Bearer admin-token")
                        .contentType("application/json")
                        .content("{\"memberIds\":[" + id + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()))
                // Exact public envelope also excludes exception names, messages and stack traces.
                .andExpect(content().json("""
                        {"success":false,"message":"입력값이 올바르지 않습니다.","data":null}
                        """, JsonCompareMode.STRICT))
                .andReturn();

        assertThat(result.getResolvedException())
                .isInstanceOf(HttpMessageNotReadableException.class)
                .hasCauseInstanceOf(JsonMappingException.class)
                .hasRootCauseInstanceOf(InputCoercionException.class);
        verifyNoInteractions(memberReviewService);
    }

    @Test
    void rawArrayLimitIsCheckedBeforeDeduplication() throws Exception {
        authenticate("admin-token",7L,"ADMIN");
        when(memberReviewService.bulkApprove(any(),eq(7L))).thenReturn(new BulkMemberApprovalResponse(1,0,1,
                java.util.List.of(new BulkMemberApprovalResponse.Result(12L,BulkMemberApprovalResponse.Outcome.SKIPPED,BulkMemberApprovalResponse.Reason.NOT_FOUND))));
        String hundred = "{\"memberIds\":[" + String.join(",", Collections.nCopies(100,"12")) + "]}";
        mockMvc.perform(post("/api/v1/admin/member-applications/bulk-approve").header("Authorization","Bearer admin-token")
                .contentType("application/json").content(hundred)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestedCount").value(1)).andExpect(jsonPath("$.data.skippedCount").value(1));
        verify(memberReviewService).bulkApprove(any(BulkMemberApprovalRequest.class),eq(7L));
        mockMvc.perform(post("/api/v1/admin/member-applications/bulk-approve").header("Authorization","Bearer admin-token")
                .contentType("application/json").content(hundred.replace("]",",12]"))).andExpect(status().isBadRequest());
        org.mockito.Mockito.verify(memberReviewService,org.mockito.Mockito.times(1)).bulkApprove(any(),any());
    }

    @Test
    void bulkApprovalRequiresAuthenticationAndAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/admin/member-applications/bulk-approve").contentType("application/json")
                .content("{\"memberIds\":[12]}")).andExpect(status().isUnauthorized());
        authenticate("student-token",8L,"STUDENT");
        mockMvc.perform(post("/api/v1/admin/member-applications/bulk-approve").header("Authorization","Bearer student-token")
                .contentType("application/json").content("{\"memberIds\":[12]}")).andExpect(status().isForbidden());
        verifyNoInteractions(memberReviewService);
    }
}
