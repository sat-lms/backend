package com.sat.lms.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.nio.charset.StandardCharsets;

@Schema(description = "계정 복구 신청 요청")
public class ReactivationRequest {
    private static final int PASSWORD_MAX_BYTES = 72;

    @Schema(description = "기존 학번", example = "20269997")
    @NotBlank(message = "학번은 필수입니다.")
    @Pattern(regexp = "^\\d{8,10}$", message = "학번은 숫자 8~10자리여야 합니다.")
    private String studentNumber;

    @Schema(description = "현재 비밀번호", example = "Test1234!", accessMode = Schema.AccessMode.WRITE_ONLY)
    @NotBlank(message = "현재 비밀번호는 필수입니다.")
    private String currentPassword;

    @Schema(description = "현재 비밀번호 확인", example = "Test1234!", accessMode = Schema.AccessMode.WRITE_ONLY)
    @NotBlank(message = "비밀번호 확인은 필수입니다.")
    private String passwordConfirm;

    protected ReactivationRequest() {
    }

    public ReactivationRequest(String studentNumber, String currentPassword, String passwordConfirm) {
        this.studentNumber = studentNumber;
        this.currentPassword = currentPassword;
        this.passwordConfirm = passwordConfirm;
    }

    public String getStudentNumber() { return studentNumber; }
    public String getCurrentPassword() { return currentPassword; }
    public String getPasswordConfirm() { return passwordConfirm; }

    @AssertTrue(message = "비밀번호는 72바이트(한글 약 24자) 이하여야 합니다.")
    private boolean isPasswordByteLengthValid() {
        return withinLimit(currentPassword) && withinLimit(passwordConfirm);
    }

    private boolean withinLimit(String value) {
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= PASSWORD_MAX_BYTES;
    }
}
