package com.sat.lms.admin.controller;

import com.sat.lms.admin.dto.AdminMemberDetailResponse;
import com.sat.lms.admin.dto.AdminMemberResponse;
import com.sat.lms.admin.service.AdminMemberService;
import com.sat.lms.global.config.SecurityConfig;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberReview;
import com.sat.lms.member.entity.MemberReviewAction;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.entity.MemberStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void adminCanListMembersWithFilters() throws Exception {
        token("admin-token", 1L, "ADMIN");
        Page<AdminMemberResponse> page = new PageImpl<>(List.of(memberResponse()));
        when(service.getMembers(eq(1L), eq(MemberRole.STUDENT), eq(MemberStatus.APPROVED), eq("최인준"), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/members")
                        .param("role", "STUDENT")
                        .param("status", "APPROVED")
                        .param("keyword", "최인준")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].memberId").value(1))
                .andExpect(jsonPath("$.data.content[0].studentNumber").value("20231234"))
                .andExpect(jsonPath("$.data.content[0].role").value("STUDENT"))
                .andExpect(jsonPath("$.data.content[0].status").value("APPROVED"));
    }

    @Test
    void adminCanListMembersWithoutAnyFilters() throws Exception {
        token("admin-token", 1L, "ADMIN");
        when(service.getMembers(eq(1L), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/admin/members")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedListReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void studentCannotListMembers() throws Exception {
        token("student-token", 2L, "STUDENT");
        mockMvc.perform(get("/api/v1/admin/members")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void invalidRoleValueReturnsBadRequest() throws Exception {
        token("admin-token", 1L, "ADMIN");
        mockMvc.perform(get("/api/v1/admin/members")
                        .param("role", "NOT_A_ROLE")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private AdminMemberResponse memberResponse() {
        return new AdminMemberResponse(1L, "20231234", "최인준", MemberRole.STUDENT, MemberStatus.APPROVED,
                OffsetDateTime.now());
    }

    @Test
    void adminCanGetMemberDetail() throws Exception {
        token("admin-token", 1L, "ADMIN");
        AdminMemberDetailResponse response = detailResponse();
        when(service.getMemberDetail(1L, 2L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/members/{memberId}", 2L)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberId").value(2))
                .andExpect(jsonPath("$.data.action").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewerId").value(9))
                .andExpect(jsonPath("$.data.reviewerName").value("관리자1"));
    }

    @Test
    void unauthenticatedDetailReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members/2"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void studentCannotGetMemberDetail() throws Exception {
        token("student-token", 3L, "STUDENT");
        mockMvc.perform(get("/api/v1/admin/members/2")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void missingMemberDetailReturnsNotFound() throws Exception {
        token("admin-token", 1L, "ADMIN");
        doThrow(new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."))
                .when(service).getMemberDetail(1L, 999L);
        mockMvc.perform(get("/api/v1/admin/members/999")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 회원입니다."));
    }

    private AdminMemberDetailResponse detailResponse() {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(2L);
        when(member.getStudentNumber()).thenReturn("20231234");
        when(member.getName()).thenReturn("최인준");
        when(member.getRole()).thenReturn(MemberRole.STUDENT);
        when(member.getStatus()).thenReturn(MemberStatus.APPROVED);
        when(member.getCreatedAt()).thenReturn(OffsetDateTime.now());
        MemberReview review = new MemberReview(2L, 9L, MemberReviewAction.APPROVED, null, OffsetDateTime.now());
        return AdminMemberDetailResponse.of(member, review, "관리자1");
    }

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
