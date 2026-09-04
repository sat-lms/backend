package com.sat.lms.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

@Schema(description = "회원가입 신청 요청")
public class SignupRequest {

    // BCrypt는 비밀번호를 72바이트까지만 지원하며 넘으면 encode()가
    // IllegalArgumentException을 던진다(GlobalExceptionHandler의 catch-all로 떨어져
    // 500이 됨). 72는 "문자 수"가 아니라 UTF-8 "바이트 수" 기준이라(한글 등 멀티바이트
    // 문자는 한 글자가 여러 바이트) @Size(max=72)로는 부정확해서 바이트 길이를 직접 잰다.
    private static final int PASSWORD_MAX_BYTES = 72;

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

    // passwordConfirm은 이 필드와 값이 같은지만 서비스 계층(AuthService.signup())에서
    // 비교되고 BCrypt.encode()에는 절대 전달되지 않으므로, 여기서만 바이트 길이를
    // 검증하면 충분하다.
    @AssertTrue(message = "비밀번호는 72바이트(한글 약 24자) 이하여야 합니다.")
    private boolean isPasswordByteLengthValid() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= PASSWORD_MAX_BYTES;
    }
}
