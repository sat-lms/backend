package com.sat.lms.assignment.controller;

import com.sat.lms.assignment.dto.AssignmentCreateRequest;
import com.sat.lms.assignment.dto.AssignmentUpdateRequest;
import com.sat.lms.assignment.service.AssignmentService;
import com.sat.lms.global.config.SecurityConfig;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import com.sat.lms.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

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
        when(assignmentService.getAssignments(eq(8L), any(Pageable.class))).thenReturn(Page.empty());
        when(assignmentService.getAssignments(eq(7L), any(Pageable.class))).thenReturn(Page.empty());
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
                "{\"title\":\"   \",\"content\":\"내용\",\"dueAt\":\"2099-09-01T00:00:00\",\"allowLateSubmission\":false}",
                "{\"title\":\"제목\",\"content\":\"   \",\"dueAt\":\"2099-09-01T00:00:00\",\"allowLateSubmission\":false}",
                "{\"title\":\"제목\",\"content\":\"내용\",\"allowLateSubmission\":false}",
                "{\"title\":\"제목\",\"content\":\"내용\",\"dueAt\":\"2099-09-01T00:00:00\"}"
        };
        for (String body : invalidBodies) {
            mockMvc.perform(post("/api/v1/assignments")
                            .header("Authorization", "Bearer admin")
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void futureDueAtPassesControllerValidation() throws Exception {
        authenticate("admin", 7L, "ADMIN");

        mockMvc.perform(post("/api/v1/assignments")
                        .header("Authorization", "Bearer admin")
                        .contentType("application/json").content(createBody("2099-01-01T00:00:00")))
                .andExpect(status().isCreated());
    }

    @Test
    void offsetDueAtFormatsAreRejected() throws Exception {
        authenticate("admin", 7L, "ADMIN");

        for (String dueAt : new String[]{"2099-01-01T00:00:00Z", "2099-01-01T00:00:00+09:00"}) {
            mockMvc.perform(post("/api/v1/assignments")
                            .header("Authorization", "Bearer admin")
                            .contentType("application/json").content(createBody(dueAt)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(assignmentService);
    }

    @Test
    void pastDueAtReturnsCommonBadRequestResponse() throws Exception {
        authenticate("admin", 7L, "ADMIN");
        when(assignmentService.create(any(AssignmentCreateRequest.class), eq(7L)))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST,
                        "마감 시각은 현재보다 미래여야 합니다."));

        mockMvc.perform(post("/api/v1/assignments")
                        .header("Authorization", "Bearer admin")
                        .contentType("application/json").content(createBody("2000-01-01T00:00:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("마감 시각은 현재보다 미래여야 합니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void currentDueAtReturnsBadRequest() throws Exception {
        authenticate("admin", 7L, "ADMIN");
        when(assignmentService.create(any(AssignmentCreateRequest.class), eq(7L)))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST,
                        "마감 시각은 현재보다 미래여야 합니다."));

        mockMvc.perform(post("/api/v1/assignments")
                        .header("Authorization", "Bearer admin")
                        .contentType("application/json").content(createBody("2026-08-27T21:00:00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("마감 시각은 현재보다 미래여야 합니다."));
    }

    @Test
    void futureLocalDateTimeUpdateAndOmittedDueAtPatchAreAccepted() throws Exception {
        authenticate("admin", 7L, "ADMIN");

        mockMvc.perform(patch("/api/v1/assignments/1")
                        .header("Authorization", "Bearer admin")
                        .contentType("application/json")
                        .content("{\"dueAt\":\"2099-01-02T23:59:59\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/assignments/1")
                        .header("Authorization", "Bearer admin")
                        .contentType("application/json")
                        .content("{\"title\":\"마감 유지\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<AssignmentUpdateRequest> captor = ArgumentCaptor.forClass(AssignmentUpdateRequest.class);
        verify(assignmentService, org.mockito.Mockito.times(2)).update(eq(1L), captor.capture(), eq(7L));
        assertThat(captor.getAllValues().get(0).getDueAt())
                .isEqualTo(LocalDateTime.parse("2099-01-02T23:59:59"));
        assertThat(captor.getAllValues().get(1).isDueAtPresent()).isFalse();
    }

    @Test
    void currentOrPastDueAtUpdateReturnsBadRequest() throws Exception {
        authenticate("admin", 7L, "ADMIN");
        when(assignmentService.update(eq(1L), any(AssignmentUpdateRequest.class), eq(7L)))
                .thenThrow(new BusinessException(HttpStatus.BAD_REQUEST,
                        "마감 시각은 현재보다 미래여야 합니다."));

        for (String dueAt : new String[]{"2026-08-27T21:00:00", "2000-01-01T00:00:00"}) {
            mockMvc.perform(patch("/api/v1/assignments/1")
                            .header("Authorization", "Bearer admin")
                            .contentType("application/json")
                            .content("{\"dueAt\":\"" + dueAt + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("마감 시각은 현재보다 미래여야 합니다."));
        }
    }

    @Test
    void defaultListSortIsDueAtAscending() throws Exception {
        authenticate("student", 8L, "STUDENT");
        when(assignmentService.getAssignments(eq(8L), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/assignments").header("Authorization", "Bearer student"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(assignmentService).getAssignments(eq(8L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test
    void sizeAboveSpringDefaultMaximumIsCappedByResolver() throws Exception {
        authenticate("student", 8L, "STUDENT");
        when(assignmentService.getAssignments(eq(8L), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/assignments?size=2001")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(assignmentService).getAssignments(eq(8L), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2000);
    }

    @Test
    void nullDueAtKeepsRequiredFieldMessage() throws Exception {
        authenticate("admin", 7L, "ADMIN");

        mockMvc.perform(post("/api/v1/assignments")
                        .header("Authorization", "Bearer admin")
                        .contentType("application/json")
                        .content("{\"title\":\"과제\",\"content\":\"내용\",\"dueAt\":null,\"allowLateSubmission\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("마감 시각은 필수입니다."));
        verifyNoInteractions(assignmentService);
    }

    @Test
    void invalidPagingAndDuplicateSortParametersReturnBadRequest() throws Exception {
        authenticate("student", 8L, "STUDENT");
        when(assignmentService.getAssignments(eq(8L), any(Pageable.class))).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(1);
            assertThat(pageable.getSort().stream().count()).isEqualTo(2);
            throw new BusinessException(HttpStatus.BAD_REQUEST, "정렬 조건은 하나만 입력할 수 있습니다.");
        });

        mockMvc.perform(get("/api/v1/assignments?page=-1")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/assignments?size=0")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/assignments?size=-1")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/assignments?page=not-a-number")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/assignments?size=not-a-number")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/assignments?sort=createdAt,desc&sort=dueAt,asc")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isBadRequest());
    }

    private String validCreateBody() {
        return createBody("2099-01-01T00:00:00");
    }

    private String createBody(String dueAt) {
        return "{\"title\":\"과제\",\"content\":\"내용\","
                + "\"dueAt\":\"" + dueAt + "\",\"allowLateSubmission\":false}";
    }

    private void authenticate(String token, Long memberId, String role) {
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getMemberId(token)).thenReturn(memberId);
        when(tokenProvider.getRole(token)).thenReturn(role);
    }
}
