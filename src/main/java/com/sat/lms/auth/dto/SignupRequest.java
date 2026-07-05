package com.sat.lms.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 신청 요청")
public class SignupRequest {

    @Schema(description = "학번", example = "20231234")
    private String studentNumber;

    @Schema(description = "이름", example = "최인준")
    private String name;

    @Schema(description = "비밀번호", example = "abc12345")
    private String password;

    @Schema(description = "비밀번호 확인", example = "abc12345")
    private String passwordConfirm;

    public String getStudentNumber() {
        return studentNumber;
    }

    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    public String getPasswordConfirm() {
        return passwordConfirm;
    }
}