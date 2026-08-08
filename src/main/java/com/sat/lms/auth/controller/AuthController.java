package com.sat.lms.auth.controller;

import com.sat.lms.auth.dto.LoginRequest;
import com.sat.lms.auth.dto.LoginResponse;
import com.sat.lms.auth.dto.SignupRequest;
import com.sat.lms.auth.dto.SignupResponse;
import com.sat.lms.auth.service.AuthService;
import com.sat.lms.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth API", description = "회원가입 및 로그인 API")
@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "회원가입 신청", description = "가입 직후 역할은 STUDENT, 상태는 PENDING입니다.")
    @PostMapping("/api/v1/auth/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(
                "회원가입 신청이 완료되었습니다. 운영자 승인 후 로그인할 수 있습니다.",
                authService.signup(request)
        );
    }

    @Operation(summary = "로그인", description = "APPROVED 상태의 회원만 로그인 검증을 통과합니다. 인증 토큰은 아직 발급하지 않습니다.")
    @PostMapping("/api/v1/auth/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("로그인에 성공했습니다.", authService.login(request));
    }

    @Operation(summary = "로그아웃", description = "기존 로그아웃 엔드포인트입니다. 인증 시스템 연동 전에는 서버 상태를 변경하지 않습니다.")
    @PostMapping("/api/auth/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success("로그아웃되었습니다.", null);
    }
}
