package com.sat.lms.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "회원가입 신청 요청")
public class SignupRequest {

    @Schema(description = "학번", example = "20231234")
    @NotBlank(message = "학번은 필수입니다.")
    @Pattern(regexp = "^\\d{8,10}$", message = "학번은 숫자 8~10자리여야 합니다.")
    private String studentNumber;

    @Schema(description = "이름", example = "최인준")
    @NotBlank(message = "이름은 필수이며 공백만 입력할 수 없습니다.")
    @Size(max = 20, message = "이름은 20자 이하여야 합니다.")
    private String name;

    @Schema(description = "비밀번호", example = "abc12345")
    @NotBlank(message = "비밀번호는 필수입니다.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$", message = "비밀번호는 8자 이상이며 영문과 숫자를 포함해야 합니다.")
    private String password;

    @Schema(description = "비밀번호 확인", example = "abc12345")
    @NotBlank(message = "비밀번호 확인은 필수입니다.")
    private String passwordConfirm;

    protected SignupRequest() {
    }

    public SignupRequest(String studentNumber, String name, String password, String passwordConfirm) {
        this.studentNumber = studentNumber;
        this.name = name;
        this.password = password;
        this.passwordConfirm = passwordConfirm;
    }

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
