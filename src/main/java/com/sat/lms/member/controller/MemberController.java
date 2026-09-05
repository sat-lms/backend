package com.sat.lms.member.controller;

import com.sat.lms.global.response.ApiResponse;
import com.sat.lms.member.dto.MemberMeResponse;
import com.sat.lms.member.dto.MemberWithdrawalRequest;
import com.sat.lms.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@Tag(name = "Member API", description = "회원 정보 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {
    private final MemberService memberService;

    public MemberController(MemberService memberService) { this.memberService = memberService; }

    @Operation(summary = "내 정보 조회", description = "Bearer JWT에서 회원 ID를 확인하여 현재 회원을 조회합니다.")
    @GetMapping("/me")
    public ApiResponse<MemberMeResponse> getMe(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("내 정보를 조회했습니다.", memberService.getMe(memberId));
    }

    @Operation(summary = "회원탈퇴", description = "현재 비밀번호를 재확인한 뒤 회원 상태를 WITHDRAWN으로 변경합니다. 회원 데이터는 보존되며 기존 JWT는 이후 DB 상태 검증에서 차단됩니다. 동일 학번 재가입은 지원하지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원탈퇴 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "현재 비밀번호 입력 오류", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "미인증 또는 현재 비밀번호 불일치", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "비활성 회원", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "마지막 관리자 탈퇴 차단", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @DeleteMapping(value = "/me", consumes = "application/json")
    public ApiResponse<Void> withdraw(@Valid @RequestBody MemberWithdrawalRequest request,
                                      @AuthenticationPrincipal Long memberId) {
        memberService.withdraw(memberId, request);
        return ApiResponse.success("회원탈퇴가 완료되었습니다.", null);
    }
}
