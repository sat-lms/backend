package com.sat.lms.submission.controller;

import com.sat.lms.global.response.ApiResponse;
import com.sat.lms.global.response.PageResponse;
import com.sat.lms.submission.dto.SubmissionListResponse;
import com.sat.lms.submission.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "My Submission API", description = "내 제출 내역 API")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/api/v1/members/me/submissions")
public class MySubmissionController {
    private final SubmissionService submissionService;

    public MySubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @Operation(summary = "내 제출 내역 목록 조회",
            description = "최신 제출/수정 순(created_at 내림차순)으로 고정 정렬되어 반환됩니다.")
    @GetMapping
    public ApiResponse<PageResponse<SubmissionListResponse>> getMySubmissions(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("제출 내역을 조회했습니다.",
                PageResponse.from(submissionService.getMySubmissions(memberId, PageRequest.of(page, size))));
    }
}