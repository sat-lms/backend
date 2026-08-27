package com.sat.lms.admin.controller;

import com.sat.lms.admin.service.AdminSubmissionService;
import com.sat.lms.global.response.ApiResponse;
import com.sat.lms.submission.dto.AdminSubmissionDetailResponse;
import com.sat.lms.submission.dto.AdminSubmissionSummaryResponse;
import com.sat.lms.submission.dto.SubmissionStatusFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Submission API", description = "관리자 제출물 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminSubmissionController {
    private final AdminSubmissionService adminSubmissionService;

    public AdminSubmissionController(AdminSubmissionService adminSubmissionService) {
        this.adminSubmissionService = adminSubmissionService;
    }

    @Operation(summary = "과제별 전체 제출 현황 조회")
    @GetMapping("/assignments/{assignmentId}/submissions")
    public ApiResponse<AdminSubmissionSummaryResponse> getSubmissionStatus(
            @PathVariable Long assignmentId,
            @RequestParam(required = false) SubmissionStatusFilter status,
            Pageable pageable,
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("과제별 제출 현황을 조회했습니다.",
                adminSubmissionService.getSubmissionStatus(assignmentId, status, pageable, memberId));
    }

    @Operation(summary = "특정 제출물 상세 조회")
    @GetMapping("/submissions/{submissionId}")
    public ApiResponse<AdminSubmissionDetailResponse> getSubmissionDetail(
            @PathVariable Long submissionId, @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("제출물을 조회했습니다.",
                adminSubmissionService.getSubmissionDetail(submissionId, memberId));
    }
}
