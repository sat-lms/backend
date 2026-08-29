package com.sat.lms.submission.controller;

import com.sat.lms.global.response.ApiResponse;
import com.sat.lms.global.response.PageResponse;
import com.sat.lms.submission.dto.SubmissionListResponse;
import com.sat.lms.submission.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
    @Parameters({
            @Parameter(name = "page", description = "페이지 번호(0부터 시작)", example = "0"),
            @Parameter(name = "size", description = "페이지 크기", example = "20")
    })
    @GetMapping
    public ApiResponse<PageResponse<SubmissionListResponse>> getMySubmissions(
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("제출 내역을 조회했습니다.",
                PageResponse.from(submissionService.getMySubmissions(memberId, pageable)));
    }
}
