package com.sat.lms.submission.controller;

import com.sat.lms.global.response.ApiResponse;
import com.sat.lms.global.response.PageResponse;
import com.sat.lms.submission.dto.SubmissionCommentCreateRequest;
import com.sat.lms.submission.dto.SubmissionCommentResponse;
import com.sat.lms.submission.dto.SubmissionCommentUpdateRequest;
import com.sat.lms.submission.service.SubmissionCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Submission Comment API", description = "제출물 댓글 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
public class SubmissionCommentController {
    private final SubmissionCommentService submissionCommentService;

    public SubmissionCommentController(SubmissionCommentService submissionCommentService) {
        this.submissionCommentService = submissionCommentService;
    }

    @Operation(summary = "제출물 댓글 작성")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/submissions/{submissionId}/comments")
    public ApiResponse<SubmissionCommentResponse> create(@PathVariable Long submissionId,
                                                         @Valid @RequestBody SubmissionCommentCreateRequest request,
                                                         @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("댓글을 작성했습니다.",
                submissionCommentService.create(submissionId, memberId, request.getContent()));
    }

    @Operation(summary = "제출물 댓글 목록 조회")
    @GetMapping("/submissions/{submissionId}/comments")
    public ApiResponse<PageResponse<SubmissionCommentResponse>> getComments(
            @PathVariable Long submissionId,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("댓글 목록을 조회했습니다.",
                PageResponse.from(submissionCommentService.getComments(submissionId, memberId, pageable)));
    }

    @Operation(summary = "제출물 댓글 수정")
    @PatchMapping("/submission-comments/{commentId}")
    public ApiResponse<SubmissionCommentResponse> update(@PathVariable Long commentId,
                                                         @Valid @RequestBody SubmissionCommentUpdateRequest request,
                                                         @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("댓글을 수정했습니다.",
                submissionCommentService.update(commentId, memberId, request.getContent()));
    }

    @Operation(summary = "제출물 댓글 삭제")
    @DeleteMapping("/submission-comments/{commentId}")
    public ApiResponse<Void> delete(@PathVariable Long commentId, @AuthenticationPrincipal Long memberId) {
        submissionCommentService.delete(commentId, memberId);
        return ApiResponse.success("댓글을 삭제했습니다.", null);
    }
}
