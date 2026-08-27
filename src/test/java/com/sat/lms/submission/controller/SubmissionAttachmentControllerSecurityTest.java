package com.sat.lms.submission.controller;

import com.sat.lms.global.config.SecurityConfig;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.submission.dto.SubmissionAttachmentDownloadUrlResponse;
import com.sat.lms.submission.service.SubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubmissionAttachmentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SubmissionAttachmentControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean SubmissionService submissionService;
    @MockitoBean JwtTokenProvider tokenProvider;

    @Test
    void authenticatedStudentCanGetDownloadUrl() throws Exception {
        authenticate("student", 8L, "STUDENT");
        when(submissionService.getDownloadUrl(10L, 8L))
                .thenReturn(new SubmissionAttachmentDownloadUrlResponse("https://example.com/signed", 300L, "a.txt"));

        mockMvc.perform(get("/api/v1/submission-attachments/{attachmentId}/download-url", 10L)
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.downloadUrl").value("https://example.com/signed"))
                .andExpect(jsonPath("$.data.expiresIn").value(300))
                .andExpect(jsonPath("$.data.originalName").value("a.txt"));

        verify(submissionService).getDownloadUrl(10L, 8L);
    }

    @Test
    void unauthenticatedDownloadUrlRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/submission-attachments/{attachmentId}/download-url", 10L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedStudentCanDeleteAttachment() throws Exception {
        authenticate("student", 8L, "STUDENT");
        doNothing().when(submissionService).deleteAttachment(10L, 8L);

        mockMvc.perform(delete("/api/v1/submission-attachments/{attachmentId}", 10L)
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isOk());

        verify(submissionService).deleteAttachment(10L, 8L);
    }

    @Test
    void unauthenticatedDeleteAttachmentReturnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/submission-attachments/{attachmentId}", 10L))
                .andExpect(status().isUnauthorized());
    }

    private void authenticate(String token, Long memberId, String role) {
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getMemberId(token)).thenReturn(memberId);
        when(tokenProvider.getRole(token)).thenReturn(role);
    }
}