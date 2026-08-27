package com.sat.lms.assignment.controller;

import com.sat.lms.assignment.service.AssignmentService;
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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssignmentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AssignmentControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AssignmentService assignmentService;
    @MockitoBean JwtTokenProvider tokenProvider;

    @Test
    void adminCanCreateUpdateAndDelete() throws Exception {
        authenticate("admin", 7L, "ADMIN");
        mockMvc.perform(post("/api/v1/assignments")
                        .header("Authorization", "Bearer admin")
                        .contentType("application/json")
                        .content(validCreateBody()))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/api/v1/assignments/1")
                        .header("Authorization", "Bearer admin")
                        .contentType("application/json")
                        .content("{\"title\":\"수정 과제\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/assignments/1")
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
        verify(assignmentService).delete(1L, 7L);
    }

    @Test
    void studentCannotCreateUpdateOrDelete() throws Exception {
        authenticate("student", 8L, "STUDENT");
        mockMvc.perform(post("/api/v1/assignments")
                        .header("Authorization", "Bearer student")
                        .contentType("application/json").content(validCreateBody()))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/assignments/1")
                        .header("Authorization", "Bearer student")
                        .contentType("application/json").content("{\"title\":\"수정\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/assignments/1")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedStudentAndAdminCanListAndReadDetail() throws Exception {
        when(assignmentService.getAssignments(eq(8L), anyInt(), anyInt(), anyString())).thenReturn(Page.empty());
        when(assignmentService.getAssignments(eq(7L), anyInt(), anyInt(), anyString())).thenReturn(Page.empty());
        authenticate("student", 8L, "STUDENT");
        authenticate("admin", 7L, "ADMIN");

        mockMvc.perform(get("/api/v1/assignments").header("Authorization", "Bearer student"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/assignments/1").header("Authorization", "Bearer student"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/assignments").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/assignments/1").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedCrudRequestsReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/assignments")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/assignments/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/assignments")
                        .contentType("application/json").content(validCreateBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/assignments/1")
                        .contentType("application/json").content("{\"title\":\"수정\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/assignments/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void createBeanValidationRejectsMissingAndBlankFields() throws Exception {
        authenticate("admin", 7L, "ADMIN");
        String[] invalidBodies = {
                "{}",
                "{\"title\":\"   \",\"content\":\"내용\",\"dueAt\":\"2026-09-01T00:00:00Z\",\"allowLateSubmission\":false}",
                "{\"title\":\"제목\",\"content\":\"   \",\"dueAt\":\"2026-09-01T00:00:00Z\",\"allowLateSubmission\":false}",
                "{\"title\":\"제목\",\"content\":\"내용\",\"allowLateSubmission\":false}",
                "{\"title\":\"제목\",\"content\":\"내용\",\"dueAt\":\"2026-09-01T00:00:00Z\"}"
        };
        for (String body : invalidBodies) {
            mockMvc.perform(post("/api/v1/assignments")
                            .header("Authorization", "Bearer admin")
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void invalidPagingAndDuplicateSortParametersReturnBadRequest() throws Exception {
        authenticate("student", 8L, "STUDENT");

        mockMvc.perform(get("/api/v1/assignments?page=-1")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/assignments?size=0")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/assignments?sort=createdAt,desc&sort=dueAt,asc")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isBadRequest());
    }

    private String validCreateBody() {
        return "{\"title\":\"과제\",\"content\":\"내용\","
                + "\"dueAt\":\"2026-09-01T00:00:00Z\",\"allowLateSubmission\":false}";
    }

    private void authenticate(String token, Long memberId, String role) {
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getMemberId(token)).thenReturn(memberId);
        when(tokenProvider.getRole(token)).thenReturn(role);
    }
}
