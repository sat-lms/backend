package com.sat.lms.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 응답")
public class LoginResponse {

    @Schema(description = "회원 ID", example = "1")
    private final Long memberId;

    @Schema(description = "학번", example = "20231234")
    private final String studentNumber;

    @Schema(description = "이름", example = "최인준")
    private final String name;

    @Schema(description = "역할", example = "STUDENT")
    private final String role;

    @Schema(description = "계정 상태", example = "APPROVED")
    private final String status;

    public LoginResponse(Long memberId, String studentNumber, String name, String role, String status) {
        this.memberId = memberId;
        this.studentNumber = studentNumber;
        this.name = name;
        this.role = role;
        this.status = status;
    }

    public Long getMemberId() {
        return memberId;
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

    public String getStatus() {
        return status;
    }
}
