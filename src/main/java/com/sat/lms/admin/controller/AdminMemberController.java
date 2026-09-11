package com.sat.lms.admin.controller;

import com.sat.lms.admin.dto.AdminMemberDetailResponse;
import com.sat.lms.admin.dto.AdminMemberResponse;
import com.sat.lms.admin.service.AdminMemberService;
import com.sat.lms.global.response.ApiResponse;
import com.sat.lms.global.response.PageResponse;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.entity.MemberStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Member API", description = "관리자 회원 관리 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/members")
public class AdminMemberController {
    private final AdminMemberService adminMemberService;

    public AdminMemberController(AdminMemberService adminMemberService) {
        this.adminMemberService = adminMemberService;
    }

    @Operation(summary = "전체 회원 목록 조회",
            description = "관리자가 역할/상태/검색어로 회원을 조회합니다. 파라미터를 생략하면 해당 조건 없이 조회하며, "
                    + "keyword는 학번 또는 이름에 부분 일치하면 매칭됩니다. WITHDRAWN 회원도 별도 처리 없이 조회됩니다.")
    @GetMapping
    public ApiResponse<PageResponse<AdminMemberResponse>> getMembers(
            @Parameter(description = "역할 필터", example = "STUDENT") @RequestParam(required = false) MemberRole role,
            @Parameter(description = "상태 필터", example = "APPROVED") @RequestParam(required = false) MemberStatus status,
            @Parameter(description = "학번 또는 이름 검색어") @RequestParam(required = false) String keyword,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal Long adminId) {
        return ApiResponse.success("회원 목록을 조회했습니다.",
                PageResponse.from(adminMemberService.getMembers(adminId, role, status, keyword, pageable)));
    }

    @Operation(summary = "특정 회원 상세 조회",
            description = "회원 기본 정보와 가장 최근 심사 기록(있으면)을 함께 조회합니다. "
                    + "심사 기록이 없거나 여러 건이어도 가장 최근 기록 하나만 반환합니다.")
    @GetMapping("/{memberId}")
    public ApiResponse<AdminMemberDetailResponse> getMemberDetail(@PathVariable Long memberId,
                                                                  @AuthenticationPrincipal Long adminId) {
        return ApiResponse.success("회원 상세 정보를 조회했습니다.",
                adminMemberService.getMemberDetail(adminId, memberId));
    }

    @Operation(summary = "학생 회원 추방", description = "APPROVED ADMIN이 STUDENT 회원을 소프트 삭제합니다. 회원과 연관 데이터는 보존되며 자기 자신과 다른 ADMIN은 추방할 수 없습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원 추방 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "미인증", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "관리자 권한 없음, 비활성 회원 또는 추방 불가 대상", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "대상 회원 없음", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "동시 처리 또는 데이터 제약 충돌", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @DeleteMapping("/{memberId}")
    public ApiResponse<Void> expel(@PathVariable Long memberId, @AuthenticationPrincipal Long adminId) {
        adminMemberService.expel(adminId, memberId);
        return ApiResponse.success("회원을 추방했습니다.", null);
    }
}
