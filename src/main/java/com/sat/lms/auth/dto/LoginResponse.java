package com.sat.lms.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 응답")
public class LoginResponse {

    @Schema(description = "Access Token", example = "jwt-token-value")
    private final String accessToken;

    @Schema(description = "사용자 ID", example = "1")
    private final Long userId;

    @Schema(description = "학번", example = "20231234")
    private final String studentNumber;

    @Schema(description = "이름", example = "최인준")
    private final String name;

    @Schema(description = "역할", example = "STUDENT")
    private final String role;

    public LoginResponse(String accessToken, Long userId, String studentNumber, String name, String role) {
        this.accessToken = accessToken;
        this.userId = userId;
        this.studentNumber = studentNumber;
        this.name = name;
        this.role = role;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public Long getUserId() {
        return userId;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }
}