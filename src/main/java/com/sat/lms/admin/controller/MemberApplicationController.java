package com.sat.lms.admin.controller;

import com.sat.lms.admin.dto.MemberApplicationResponse;
import com.sat.lms.admin.dto.MemberReviewRequest;
import com.sat.lms.admin.dto.MemberReviewResponse;
import com.sat.lms.admin.service.MemberApplicationService;
import com.sat.lms.admin.service.MemberReviewService;
import com.sat.lms.global.response.ApiResponse;
import com.sat.lms.global.response.PageResponse;
import com.sat.lms.member.entity.MemberStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Member Application API", description = "회원 가입 신청 관리 API")
@RestController
@RequestMapping("/api/v1/admin/member-applications")
public class MemberApplicationController {

    private final MemberApplicationService memberApplicationService;
    private final MemberReviewService memberReviewService;

    public MemberApplicationController(MemberApplicationService memberApplicationService, MemberReviewService memberReviewService) {
        this.memberApplicationService = memberApplicationService;
        this.memberReviewService = memberReviewService;
    }

    @Operation(
            summary = "가입 신청 목록 조회",
            description = "관리자가 회원 가입 신청 목록을 조회합니다. status 를 지정하지 않으면 승인 대기(PENDING) 상태를 신청일시 오름차순으로 조회합니다."
    )
    @GetMapping
    public ApiResponse<PageResponse<MemberApplicationResponse>> getMemberApplications(
            @Parameter(description = "조회할 가입 신청 상태", example = "PENDING")
            @RequestParam(required = false, defaultValue = "PENDING") MemberStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<MemberApplicationResponse> applications = memberApplicationService.getMemberApplications(status, pageable);
        return ApiResponse.success("가입 신청 목록을 조회했습니다.", PageResponse.from(applications));
    }

    @Operation(
            summary = "가입 신청 승인/거절",
            description = "관리자가 가입 신청을 심사합니다. action=APPROVED 이면 승인, action=REJECTED 이면 rejectionReason 이 필수입니다. "
                    + "인증 연동 전까지 심사자 ID는 X-Admin-Id 헤더로 임시 전달합니다."
    )
    @PatchMapping("/{memberId}")
    public ApiResponse<MemberReviewResponse> reviewMemberApplication(
            @Parameter(description = "심사 대상 회원 ID", example = "1")
            @PathVariable Long memberId,
            @RequestBody MemberReviewRequest request,
            @Parameter(description = "심사자(관리자) 회원 ID", example = "2")
            @RequestHeader("X-Admin-Id") Long reviewerId
    ) {
        MemberReviewResponse response = memberReviewService.review(memberId, request, reviewerId);
        return ApiResponse.success("가입 신청을 처리했습니다.", response);
    }
}