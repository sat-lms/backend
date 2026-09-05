package com.sat.lms.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MemberWithdrawalRequest {
    @Schema(description = "현재 비밀번호", example = "Password123", accessMode = Schema.AccessMode.WRITE_ONLY)
    @NotBlank(message = "현재 비밀번호를 입력해주세요.")
    @Size(max = 100, message = "현재 비밀번호는 100자 이하여야 합니다.")
    private String currentPassword;

    protected MemberWithdrawalRequest() {
    }

    public MemberWithdrawalRequest(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }
}
