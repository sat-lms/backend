package com.sat.lms.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 신청 응답")
public class SignupResponse {

    @Schema(description = "사용자 ID", example = "1")
    private final Long userId;

    @Schema(description = "학번", example = "20231234")
    private final String studentNumber;

    @Schema(description = "이름", example = "최인준")
    private final String name;

    @Schema(description = "계정 상태", example = "PENDING")
    private final String status;

    public SignupResponse(Long userId, String studentNumber, String name, String status) {
        this.userId = userId;
        this.studentNumber = studentNumber;
        this.name = name;
        this.status = status;
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

    public String getStatus() {
        return status;
    }
}