package com.sat.lms.admin.controller;

import com.sat.lms.admin.dto.MemberApplicationResponse;
import com.sat.lms.admin.dto.MemberReviewRequest;
import com.sat.lms.admin.dto.MemberReviewResponse;
import com.sat.lms.admin.dto.BulkMemberApprovalRequest;
import com.sat.lms.admin.dto.BulkMemberApprovalResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.PostMapping;
import com.sat.lms.admin.service.MemberApplicationService;
import com.sat.lms.admin.service.MemberReviewService;
import com.sat.lms.global.response.ApiResponse;
import com.sat.lms.global.response.PageResponse;
import com.sat.lms.member.entity.MemberStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Member Application API", description = "회원 가입 신청 관리 API")
@SecurityRequirement(name = "bearerAuth")
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
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<MemberApplicationResponse> applications = memberApplicationService.getMemberApplications(status, pageable);
        return ApiResponse.success("가입 신청 목록을 조회했습니다.", PageResponse.from(applications));
    }

    @Operation(
            summary = "가입 신청 승인/거절",
            description = "관리자가 가입 신청을 심사합니다. action=APPROVED 이면 승인, action=REJECTED 이면 rejectionReason 이 필수입니다."
    )
    @PatchMapping("/{memberId}")
    public ApiResponse<MemberReviewResponse> reviewMemberApplication(
            @Parameter(description = "심사 대상 회원 ID", example = "1")
            @PathVariable Long memberId,
            @Valid @RequestBody MemberReviewRequest request,
            @AuthenticationPrincipal Long reviewerId
    ) {
        MemberReviewResponse response = memberReviewService.review(memberId, request, reviewerId);
        return ApiResponse.success("가입 신청을 처리했습니다.", response);
    }

    @Operation(summary = "가입 신청 일괄 승인", description = "APPROVED ADMIN 전용이며 DB의 현재 권한을 잠금 후 검증합니다. "
            + "원본 memberIds 배열은 1~100개의 양의 정수 ID여야 합니다. 중복은 제거하며 requestedCount는 중복 제거 후 ID 수입니다. "
            + "results는 최초 요청 순서입니다. PENDING만 승인하고 NOT_PENDING(그 외 상태), NOT_FOUND(없는 회원)는 제외합니다. "
            + "모두 제외되어도 200입니다. 요청 전체가 하나의 트랜잭션이며 시스템 오류 시 상태와 심사 이력 전체가 rollback됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "처리 결과",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"success":true,"message":"가입 신청 일괄 승인을 처리했습니다.","data":{"requestedCount":3,"approvedCount":1,"skippedCount":2,"results":[{"memberId":12,"result":"APPROVED","reason":null},{"memberId":15,"result":"SKIPPED","reason":"NOT_PENDING"},{"memberId":18,"result":"SKIPPED","reason":"NOT_FOUND"}]}}
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "본문/JSON/ID 오류 또는 원본 배열 100개 초과", content = @Content(examples = @ExampleObject(value = "{\"success\":false,\"message\":\"입력값이 올바르지 않습니다.\",\"data\":null}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "미인증", content = @Content(examples = @ExampleObject(value = "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"data\":null}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "현재 DB 상태·역할이 APPROVED ADMIN이 아님", content = @Content(examples = @ExampleObject(value = "{\"success\":false,\"message\":\"접근 권한이 없습니다.\",\"data\":null}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "DB 제약 위반: 전체 rollback", content = @Content(examples = @ExampleObject(value = "{\"success\":false,\"message\":\"중복되거나 제약조건에 위배되는 데이터입니다.\",\"data\":null}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "시스템 오류: 전체 rollback", content = @Content(examples = @ExampleObject(value = "{\"success\":false,\"message\":\"서버 내부 오류가 발생했습니다.\",\"data\":null}")))
    })
    @PostMapping("/bulk-approve")
    public ApiResponse<BulkMemberApprovalResponse> bulkApprove(
            @Valid @RequestBody BulkMemberApprovalRequest request,
            @AuthenticationPrincipal Long reviewerId) {
        return ApiResponse.success("가입 신청 일괄 승인을 처리했습니다.", memberReviewService.bulkApprove(request, reviewerId));
    }
}
