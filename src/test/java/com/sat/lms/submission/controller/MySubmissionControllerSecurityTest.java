package com.sat.lms.submission.controller;

import com.sat.lms.global.config.SecurityConfig;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.submission.dto.SubmissionListResponse;
import com.sat.lms.submission.dto.MySubmissionSort;
import com.sat.lms.submission.service.SubmissionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MySubmissionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class MySubmissionControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean SubmissionService submissionService;
    @MockitoBean JwtTokenProvider tokenProvider;

    @Test
    void authenticatedStudentCanListOwnSubmissions() throws Exception {
        authenticate("student", 8L, "STUDENT");
        SubmissionListResponse item = new SubmissionListResponse(1L, 2L, "과제", "내용", false,
                OffsetDateTime.now(), OffsetDateTime.now());
        Page<SubmissionListResponse> page = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);
        when(submissionService.getMySubmissions(eq(8L), eq(true), eq(MySubmissionSort.DUE_AT_DESC), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/members/me/submissions")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].assignmentTitle").value("과제"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(submissionService).getMySubmissions(eq(8L), eq(true), eq(MySubmissionSort.DUE_AT_DESC), any());
    }

    @Test
    void adminAttemptIsForbidden() throws Exception {
        authenticate("admin", 7L, "ADMIN");
        when(submissionService.getMySubmissions(eq(7L), eq(true), eq(MySubmissionSort.DUE_AT_DESC), any()))
                .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "학생만 이용할 수 있는 기능입니다."));

        mockMvc.perform(get("/api/v1/members/me/submissions")
                        .header("Authorization", "Bearer admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void unauthenticatedRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/members/me/submissions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pageableParametersArePassedToServiceForFixedSortNormalization() throws Exception {
        authenticate("student", 8L, "STUDENT");
        Page<SubmissionListResponse> page = new PageImpl<>(List.of(), PageRequest.of(1, 5), 0);
        when(submissionService.getMySubmissions(eq(8L), eq(true), eq(MySubmissionSort.SUBMITTED_AT_DESC), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/members/me/submissions")
                        .param("page", "1").param("size", "5").param("sort", "submittedAtDesc")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(submissionService).getMySubmissions(eq(8L), eq(true), eq(MySubmissionSort.SUBMITTED_AT_DESC), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort().getOrderFor("submittedAtDesc")).isNotNull();
    }

    @Test
    void includeFalseAndDueAtAscArePassedAsFixedOptions() throws Exception {
        authenticate("student", 8L, "STUDENT");
        when(submissionService.getMySubmissions(eq(8L), eq(false), eq(MySubmissionSort.DUE_AT_ASC), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/members/me/submissions")
                        .param("includeNotSubmitted", "false").param("sort", "dueAtAsc")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isOk());
        verify(submissionService).getMySubmissions(eq(8L), eq(false), eq(MySubmissionSort.DUE_AT_ASC), any());
    }

    @Test
    void invalidOrRepeatedFixedParametersReturnBadRequest() throws Exception {
        authenticate("student", 8L, "STUDENT");
        for (String invalid : new String[]{"yes", "TRUE", "", " true"}) {
            mockMvc.perform(get("/api/v1/members/me/submissions")
                            .param("includeNotSubmitted", invalid)
                            .header("Authorization", "Bearer student"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/v1/members/me/submissions")
                        .param("sort", "createdAt,desc")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/members/me/submissions")
                        .param("sort", "dueAtAsc", "dueAtDesc")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isBadRequest());
    }

    private void authenticate(String token, Long memberId, String role) {
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getMemberId(token)).thenReturn(memberId);
        when(tokenProvider.getRole(token)).thenReturn(role);
    }
}
