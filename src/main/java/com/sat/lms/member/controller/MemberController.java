package com.sat.lms.member.controller;

import com.sat.lms.global.response.ApiResponse;
import com.sat.lms.member.dto.MemberMeResponse;
import com.sat.lms.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Member API", description = "회원 정보 API")
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {
    private final MemberService memberService;

    public MemberController(MemberService memberService) { this.memberService = memberService; }

    @Operation(summary = "내 정보 조회", description = "Bearer JWT에서 회원 ID를 확인하여 현재 회원을 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ApiResponse<MemberMeResponse> getMe(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("내 정보를 조회했습니다.", memberService.getMe(memberId));
    }
}
