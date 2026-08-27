package com.sat.lms.admin.controller;

import com.sat.lms.admin.service.AdminSubmissionService;
import com.sat.lms.global.config.SecurityConfig;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.global.response.PageResponse;
import com.sat.lms.submission.dto.AdminSubmissionDetailResponse;
import com.sat.lms.submission.dto.AdminSubmissionStudentRow;
import com.sat.lms.submission.dto.AdminSubmissionSummaryResponse;
import com.sat.lms.submission.dto.SubmissionStatusFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminSubmissionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminSubmissionControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AdminSubmissionService adminSubmissionService;
    @MockitoBean JwtTokenProvider tokenProvider;

    @Test
    void adminCanGetSubmissionStatus() throws Exception {
        authenticate("admin", 7L, "ADMIN");
        AdminSubmissionStudentRow row = mock(AdminSubmissionStudentRow.class);
        when(row.getStudentNumber()).thenReturn("20231234");
        Page<AdminSubmissionStudentRow> page = new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1);
        AdminSubmissionSummaryResponse response = new AdminSubmissionSummaryResponse(1, 0, 1,
                PageResponse.from(page));
        when(adminSubmissionService.getSubmissionStatus(eq(1L), isNull(), any(), eq(7L))).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/assignments/{assignmentId}/submissions", 1L)
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submittedCount").value(1))
                .andExpect(jsonPath("$.data.students.content[0].studentNumber").value("20231234"));

        verify(adminSubmissionService).getSubmissionStatus(eq(1L), isNull(), any(), eq(7L));
    }

    @Test
    void adminCanFilterSubmissionStatusByStatus() throws Exception {
        authenticate("admin", 7L, "ADMIN");
        Page<AdminSubmissionStudentRow> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        AdminSubmissionSummaryResponse response = new AdminSubmissionSummaryResponse(0, 0, 0,
                PageResponse.from(page));
        when(adminSubmissionService.getSubmissionStatus(eq(1L), eq(SubmissionStatusFilter.LATE), any(), eq(7L)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/assignments/{assignmentId}/submissions", 1L)
                        .param("status", "LATE")
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());

        verify(adminSubmissionService).getSubmissionStatus(eq(1L), eq(SubmissionStatusFilter.LATE), any(), eq(7L));
    }

    @Test
    void studentCannotGetSubmissionStatus() throws Exception {
        authenticate("student", 8L, "STUDENT");

        mockMvc.perform(get("/api/v1/admin/assignments/{assignmentId}/submissions", 1L)
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotGetSubmissionStatus() throws Exception {
        mockMvc.perform(get("/api/v1/admin/assignments/{assignmentId}/submissions", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanGetSubmissionDetail() throws Exception {
        authenticate("admin", 7L, "ADMIN");
        AdminSubmissionDetailResponse response = mock(AdminSubmissionDetailResponse.class);
        when(response.getSubmissionId()).thenReturn(5L);
        when(response.getStudentNumber()).thenReturn("20231234");
        when(adminSubmissionService.getSubmissionDetail(5L, 7L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/submissions/{submissionId}", 5L)
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submissionId").value(5))
                .andExpect(jsonPath("$.data.studentNumber").value("20231234"));

        verify(adminSubmissionService).getSubmissionDetail(5L, 7L);
    }

    @Test
    void studentCannotGetSubmissionDetail() throws Exception {
        authenticate("student", 8L, "STUDENT");

        mockMvc.perform(get("/api/v1/admin/submissions/{submissionId}", 5L)
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedCannotGetSubmissionDetail() throws Exception {
        mockMvc.perform(get("/api/v1/admin/submissions/{submissionId}", 5L))
                .andExpect(status().isUnauthorized());
    }

    private void authenticate(String token, Long memberId, String role) {
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getMemberId(token)).thenReturn(memberId);
        when(tokenProvider.getRole(token)).thenReturn(role);
    }
}
