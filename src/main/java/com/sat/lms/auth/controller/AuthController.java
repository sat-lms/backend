package com.sat.lms.auth.controller;

import com.sat.lms.auth.dto.LoginRequest;
import com.sat.lms.auth.dto.LoginResponse;
import com.sat.lms.auth.dto.ReactivationRequest;
import com.sat.lms.auth.dto.SignupRequest;
import com.sat.lms.auth.dto.SignupResponse;
import com.sat.lms.auth.service.AuthService;
import com.sat.lms.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth API", description = "회원가입 및 로그인 API")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "회원가입 신청", description = "가입 직후 역할은 STUDENT, 상태는 PENDING입니다.")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "429", description = "IP별 회원가입 요청 한도 초과",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))))
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(
                "회원가입 신청이 완료되었습니다. 운영자 승인 후 로그인할 수 있습니다.",
                authService.signup(request)
        );
    }

    @Operation(summary = "로그인", description = "APPROVED 회원만 로그인할 수 있습니다. 계정 존재, 비밀번호 일치, "
            + "회원 상태 중 어느 조건이 실패했는지 구분하지 않고 동일한 401 응답을 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 실패: 학번 또는 비밀번호가 올바르지 않습니다.",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {"success":false,"message":"학번 또는 비밀번호가 올바르지 않습니다.","data":null}
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429", description = "IP별 로그인 요청 한도 초과",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("로그인에 성공했습니다.", authService.login(request));
    }

    @Operation(summary = "계정 복구 신청", description = "자진 탈퇴한 기존 회원만 신청할 수 있습니다. 신청 후 PENDING 상태가 되며 관리자 재승인 전에는 로그인하거나 JWT를 발급받을 수 없습니다. 관리자에게 추방된 회원은 복구할 수 없습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "복구 신청 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 필드 또는 비밀번호 확인 오류", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "계정 복구 정보 확인 실패", content = @Content(schema = @Schema(implementation = ApiResponse.class), examples = @ExampleObject(value = "{\"success\":false,\"message\":\"계정 복구 정보를 확인할 수 없습니다.\",\"data\":null}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "IP별 복구 신청 요청 한도 초과", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    @PostMapping("/reactivation-requests")
    public ApiResponse<Void> requestReactivation(@Valid @RequestBody ReactivationRequest request) {
        authService.requestReactivation(request);
        return ApiResponse.success("계정 복구 신청이 완료되었습니다. 관리자 승인을 기다려주세요.", null);
    }

    @Operation(summary = "로그아웃", description = "인증 시스템 연동 전에는 서버 상태를 변경하지 않습니다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success("로그아웃되었습니다.", null);
    }
}
