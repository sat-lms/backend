package com.sat.lms.submission.controller;

import com.sat.lms.global.response.ApiResponse;
import com.sat.lms.submission.dto.SubmissionCreateRequest;
import com.sat.lms.submission.dto.SubmissionDetailResponse;
import com.sat.lms.submission.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Submission API", description = "과제 제출 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/assignments/{assignmentId}/submission")
public class SubmissionController {
    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) { this.submissionService = submissionService; }

    @Operation(summary = "내 과제 제출물 조회")
    @GetMapping
    public ApiResponse<SubmissionDetailResponse> getMySubmission(@PathVariable Long assignmentId,
                                                                  @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("제출물을 조회했습니다.", submissionService.getMySubmission(assignmentId, memberId));
    }

    @Operation(summary = "과제 최초 제출")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(consumes = "multipart/form-data")
    public ApiResponse<SubmissionDetailResponse> submit(@PathVariable Long assignmentId,
                                                         @RequestPart("request") SubmissionCreateRequest request,
                                                         @RequestPart(value = "files", required = false)
                                                         List<MultipartFile> files,
                                                         @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("과제를 제출했습니다.",
                submissionService.submit(assignmentId, memberId, request, files));
    }
}