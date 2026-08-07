package com.sat.lms.member.controller;

import com.sat.lms.global.response.ApiResponse;
import com.sat.lms.member.dto.MemberMeResponse;
import com.sat.lms.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Member API", description = "회원 정보 API")
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @Operation(
            summary = "내 정보 조회",
            description = "인증 연동 전까지 회원 ID는 X-Member-Id 헤더로 임시 전달합니다."
    )
    @GetMapping("/me")
    public ApiResponse<MemberMeResponse> getMe(
            @Parameter(description = "조회할 회원 ID", example = "1")
            @RequestHeader("X-Member-Id") Long memberId
    ) {
        return ApiResponse.success("내 정보를 조회했습니다.", memberService.getMe(memberId));
    }
}
