package com.sat.lms.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 요청")
public class LoginRequest {

    @Schema(description = "학번", example = "20231234")
    private String studentNumber;

    @Schema(description = "비밀번호", example = "abc12345")
    private String password;

    public String getStudentNumber() {
        return studentNumber;
    }

    public String getPassword() {
        return password;
    }
}