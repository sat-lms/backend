package com.sat.lms.submission.controller;

import com.sat.lms.global.config.SecurityConfig;
import com.sat.lms.global.security.JwtAuthenticationFilter;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.submission.dto.SubmissionCommentResponse;
import com.sat.lms.submission.entity.SubmissionComment;
import com.sat.lms.submission.service.SubmissionCommentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubmissionCommentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SubmissionCommentControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean SubmissionCommentService submissionCommentService;
    @MockitoBean JwtTokenProvider tokenProvider;

    @Test
    void authenticatedStudentCanCreateComment() throws Exception {
        authenticate("student", 8L, "STUDENT");
        SubmissionCommentResponse response = response();
        when(submissionCommentService.create(eq(1L), eq(8L), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/comments", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글입니다.\"}")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isCreated());
    }

    @Test
    void unauthenticatedCreateReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/submissions/{submissionId}/comments", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글입니다.\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedStudentCanListComments() throws Exception {
        authenticate("student", 8L, "STUDENT");
        Page<SubmissionCommentResponse> page = new PageImpl<>(List.of(response()));
        when(submissionCommentService.getComments(eq(1L), eq(8L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/submissions/{submissionId}/comments", 1L)
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedListReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/submissions/{submissionId}/comments", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedAuthorCanUpdateComment() throws Exception {
        authenticate("student", 8L, "STUDENT");
        SubmissionCommentResponse response = response();
        when(submissionCommentService.update(eq(1L), eq(8L), any())).thenReturn(response);

        mockMvc.perform(patch("/api/v1/submission-comments/{commentId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정합니다.\"}")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedUpdateReturnsUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/v1/submission-comments/{commentId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정합니다.\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanDeleteComment() throws Exception {
        authenticate("student", 8L, "STUDENT");
        doNothing().when(submissionCommentService).delete(1L, 8L);

        mockMvc.perform(delete("/api/v1/submission-comments/{commentId}", 1L)
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedDeleteReturnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/submission-comments/{commentId}", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankContentReturnsBadRequest() throws Exception {
        authenticate("student", 8L, "STUDENT");

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/comments", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}")
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingCommentOver500CharsReturnsBadRequest() throws Exception {
        authenticate("student", 8L, "STUDENT");

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/comments", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contentJson("a".repeat(501)))
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingCommentWithExactly500CharsSucceeds() throws Exception {
        authenticate("student", 8L, "STUDENT");
        SubmissionCommentResponse response = response();
        when(submissionCommentService.create(eq(1L), eq(8L), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/submissions/{submissionId}/comments", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contentJson("a".repeat(500)))
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isCreated());
    }

    @Test
    void updatingCommentOver500CharsReturnsBadRequest() throws Exception {
        authenticate("student", 8L, "STUDENT");

        mockMvc.perform(patch("/api/v1/submission-comments/{commentId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contentJson("a".repeat(501)))
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatingCommentWithExactly500CharsSucceeds() throws Exception {
        authenticate("student", 8L, "STUDENT");
        SubmissionCommentResponse response = response();
        when(submissionCommentService.update(eq(1L), eq(8L), any())).thenReturn(response);

        mockMvc.perform(patch("/api/v1/submission-comments/{commentId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contentJson("a".repeat(500)))
                        .header("Authorization", "Bearer student"))
                .andExpect(status().isOk());
    }

    private String contentJson(String content) {
        return "{\"content\":\"" + content + "\"}";
    }

    private SubmissionCommentResponse response() {
        Member author = mock(Member.class);
        when(author.getName()).thenReturn("학생");
        when(author.getRole()).thenReturn(MemberRole.STUDENT);
        SubmissionComment comment = mock(SubmissionComment.class);
        when(comment.getId()).thenReturn(1L);
        when(comment.getContent()).thenReturn("댓글입니다.");
        when(comment.getAuthor()).thenReturn(author);
        when(comment.getCreatedAt()).thenReturn(OffsetDateTime.now());
        return SubmissionCommentResponse.from(comment);
    }

    private void authenticate(String token, Long memberId, String role) {
        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getMemberId(token)).thenReturn(memberId);
        when(tokenProvider.getRole(token)).thenReturn(role);
    }
}
