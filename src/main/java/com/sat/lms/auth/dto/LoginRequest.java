package com.sat.lms.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그인 요청")
public class LoginRequest {

    @Schema(description = "학번", example = "20231234")
    @NotBlank(message = "학번은 필수입니다.")
    private String studentNumber;

    @Schema(description = "비밀번호", example = "abc12345")
    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;

    protected LoginRequest() {
    }

    public LoginRequest(String studentNumber, String password) {
        this.studentNumber = studentNumber;
        this.password = password;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public String getPassword() {
        return password;
    }
}
