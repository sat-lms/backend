package com.sat.lms.submission.controller;

import com.sat.lms.global.config.SecurityConfig;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.submission.dto.SubmissionDetailResponse;
import com.sat.lms.submission.entity.Submission;
import com.sat.lms.submission.service.SubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubmissionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SubmissionControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean SubmissionService submissionService;
    @MockitoBean JwtTokenProvider tokenProvider;

    @Test
    void studentCanSubmitUsingAuthenticatedId() throws Exception {
        authenticate("student", 8L, "STUDENT");
        SubmissionDetailResponse response = detailResponse();
        when(submissionService.submit(eq(1L), eq(8L), any(), any())).thenReturn(response);
        MockMultipartFile request = new MockMultipartFile("request", "request", "application/json",
                "{\"textContent\":\"제출합니다.\"}".getBytes());

        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", 1L)
                        .file(request)
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isCreated());

        verify(submissionService).submit(eq(1L), eq(8L), any(), any());
    }

    @Test
    void securityLayerLetsAnyAuthenticatedRoleReachControllerRoleCheckIsDeferredToService() throws Exception {
        authenticate("admin", 7L, "ADMIN");
        SubmissionDetailResponse response = detailResponse();
        when(submissionService.submit(eq(1L), eq(7L), any(), any())).thenReturn(response);
        MockMultipartFile request = new MockMultipartFile("request", "request", "application/json",
                "{\"textContent\":\"제출합니다.\"}".getBytes());

        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", 1L)
                        .file(request)
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isCreated());

        verify(submissionService).submit(eq(1L), eq(7L), any(), any());
    }

    @Test
    void unauthenticatedSubmitReturnsUnauthorized() throws Exception {
        MockMultipartFile request = new MockMultipartFile("request", "request", "application/json",
                "{\"textContent\":\"제출합니다.\"}".getBytes());

        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", 1L).file(request))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void unauthenticatedGetReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/assignments/{assignmentId}/submission", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedStudentCanReadOwnSubmission() throws Exception {
        authenticate("student", 8L, "STUDENT");
        SubmissionDetailResponse response = detailResponse();
        when(submissionService.getMySubmission(1L, 8L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/assignments/{assignmentId}/submission", 1L)
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isOk());
        verify(submissionService).getMySubmission(1L, 8L);
    }

    @Test
    void submitWithFilesPassesFilesToService() throws Exception {
        authenticate("student", 8L, "STUDENT");
        SubmissionDetailResponse response = detailResponse();
        when(submissionService.submit(eq(1L), eq(8L), any(), any())).thenReturn(response);
        MockMultipartFile request = new MockMultipartFile("request", "request", "application/json",
                "{}".getBytes());
        MockMultipartFile file = new MockMultipartFile("files", "Member.java", "text/plain", "code".getBytes());

        mockMvc.perform(multipart("/api/v1/assignments/{assignmentId}/submission", 1L)
                        .file(request).file(file)
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isCreated());

        verify(submissionService).submit(eq(1L), eq(8L), any(), eq(List.of(file)));
    }

    private SubmissionDetailResponse detailResponse() {
        Submission submission = mock(Submission.class);
        when(submission.getId()).thenReturn(1L);
        return SubmissionDetailResponse.from(submission, List.of());
    }

    private void authenticate(String token, Long memberId, String role) {
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getMemberId(token)).thenReturn(memberId);
        when(tokenProvider.getRole(token)).thenReturn(role);
    }
}