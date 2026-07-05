package com.sat.lms.auth.controller;

import com.sat.lms.auth.dto.LoginRequest;
import com.sat.lms.auth.dto.LoginResponse;
import com.sat.lms.auth.dto.SignupRequest;
import com.sat.lms.auth.dto.SignupResponse;
import com.sat.lms.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth API", description = "회원가입, 로그인, 로그아웃 API")
@RestController
public class AuthController {

    @Operation(
            summary = "회원가입 신청",
            description = "학번, 이름, 비밀번호로 회원가입을 신청합니다. 가입 직후 계정 상태는 승인 대기입니다."
    )
    @PostMapping("/api/auth/signup")
    public ApiResponse<SignupResponse> signup(@RequestBody SignupRequest request) {
        SignupResponse response = new SignupResponse(
                1L,
                request.getStudentNumber(),
                request.getName(),
                "PENDING"
        );

        return ApiResponse.success(
                "회원가입 신청이 완료되었습니다. 운영자 승인 후 로그인할 수 있습니다.",
                response
        );
    }

    @Operation(
            summary = "로그인",
            description = "승인 완료된 사용자가 학번과 비밀번호로 로그인합니다."
    )
    @PostMapping("/api/auth/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = new LoginResponse(
                "jwt-token-value",
                1L,
                request.getStudentNumber(),
                "최인준",
                "STUDENT"
        );

        return ApiResponse.success("로그인에 성공했습니다.", response);
    }

    @Operation(
            summary = "로그아웃",
            description = "로그아웃을 처리합니다. JWT 방식에서는 프론트에서 토큰을 삭제하는 방식으로 처리할 수 있습니다."
    )
    @PostMapping("/api/auth/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success("로그아웃되었습니다.", null);
    }
}