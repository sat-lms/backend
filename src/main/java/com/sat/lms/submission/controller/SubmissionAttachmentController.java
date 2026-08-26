package com.sat.lms.submission.controller;

import com.sat.lms.global.response.ApiResponse;
import com.sat.lms.submission.dto.SubmissionAttachmentDownloadUrlResponse;
import com.sat.lms.submission.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Submission Attachment API", description = "제출 파일 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/submission-attachments")
public class SubmissionAttachmentController {
    private final SubmissionService submissionService;

    public SubmissionAttachmentController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @Operation(summary = "제출 파일 다운로드 URL 발급")
    @GetMapping("/{attachmentId}/download-url")
    public ApiResponse<SubmissionAttachmentDownloadUrlResponse> getDownloadUrl(
            @PathVariable Long attachmentId, @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("다운로드 URL을 발급했습니다.", submissionService.getDownloadUrl(attachmentId, memberId));
    }

    @Operation(summary = "제출 파일 개별 삭제")
    @DeleteMapping("/{attachmentId}")
    public ApiResponse<Void> deleteAttachment(@PathVariable Long attachmentId, @AuthenticationPrincipal Long memberId) {
        submissionService.deleteAttachment(attachmentId, memberId);
        return ApiResponse.success("제출 파일을 삭제했습니다.", null);
    }
}