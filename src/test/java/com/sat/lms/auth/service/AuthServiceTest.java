package com.sat.lms.auth.service;

import com.sat.lms.auth.dto.LoginRequest;
import com.sat.lms.auth.dto.SignupRequest;
import com.sat.lms.auth.dto.SignupResponse;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.entity.MemberStatus;
import com.sat.lms.member.repository.MemberRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private MemberRepository memberRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        when(jwtTokenProvider.createAccessToken(any(), any())).thenReturn("access-token");
        when(jwtTokenProvider.getExpirationSeconds()).thenReturn(3600L);
        authService = new AuthService(memberRepository, passwordEncoder, jwtTokenProvider);
    }

    @Test
    void signupSucceedsWithStudentPendingAndEncodedPassword() {
        SignupRequest request = new SignupRequest("20231234", " 홍길동 ", "password1", "password1");
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            OffsetDateTime auditedAt = OffsetDateTime.now(ZoneOffset.UTC);
            ReflectionTestUtils.setField(member, "createdAt", auditedAt);
            ReflectionTestUtils.setField(member, "updatedAt", auditedAt);
            return member;
        });

        SignupResponse response = authService.signup(request);

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(captor.capture());
        Member saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(MemberRole.STUDENT);
        assertThat(saved.getStatus()).isEqualTo(MemberStatus.PENDING);
        assertThat(saved.getName()).isEqualTo("홍길동");
        assertThat(saved.getPasswordHash()).isNotEqualTo("password1");
        assertThat(passwordEncoder.matches("password1", saved.getPasswordHash())).isTrue();
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getCreatedAt()).isNotNull();
    }

    @Test
    void signupRejectsDuplicateStudentNumber() {
        SignupRequest request = new SignupRequest("20231234", "홍길동", "password1", "password1");
        when(memberRepository.existsByStudentNumber("20231234")).thenReturn(true);

        assertBusinessException(() -> authService.signup(request), HttpStatus.CONFLICT);
    }

    @Test
    void signupRejectsPasswordMismatch() {
        SignupRequest request = new SignupRequest("20231234", "홍길동", "password1", "password2");

        assertBusinessException(() -> authService.signup(request), HttpStatus.BAD_REQUEST);
    }

    @Test
    void signupRequestRejectsInvalidStudentNumberFormat() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        SignupRequest request = new SignupRequest("20A12", "홍길동", "password1", "password1");

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("studentNumber"));
    }

    @Test
    void signupRequestRejects73ByteAsciiPassword() {
        // BCrypt encode()가 IllegalArgumentException을 던지는 경계(#83) — ASCII 73자는 73바이트.
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        String password73Bytes = "a1" + "b".repeat(71);
        SignupRequest request = new SignupRequest("20231234", "홍길동", password73Bytes, password73Bytes);

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getMessage().contains("72바이트"));
    }

    @Test
    void signupRequestAcceptsExactly72ByteAsciiPassword() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        String password72Bytes = "a1" + "b".repeat(70);
        assertThat(password72Bytes.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(72);
        SignupRequest request = new SignupRequest("20231234", "홍길동", password72Bytes, password72Bytes);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void signupRequestRejectsMultibytePasswordUnder72CharsButOver72Bytes() {
        // 핵심 검증: 문자 수 기준(@Size(max=72))이었다면 통과했을 값이 바이트 기준으로는
        // 막혀야 한다. "가"는 UTF-8로 3바이트 — "a1" + "가"*24 = 26자(문자 수 기준 통과권)지만
        // 2 + 24*3 = 74바이트로 72바이트를 초과한다.
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        String password = "a1" + "가".repeat(24);
        assertThat(password.length()).isLessThan(72);
        assertThat(password.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSizeGreaterThan(72);
        SignupRequest request = new SignupRequest("20231234", "홍길동", password, password);

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getMessage().contains("72바이트"));
    }

    @Test
    void signupRequestAcceptsNormalShortPassword() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        SignupRequest request = new SignupRequest("20231234", "홍길동", "password1", "password1");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void serviceLayerAloneStillThrowsBCryptExceptionWithoutControllerLevelValidation() {
        // DTO의 @AssertTrue는 컨트롤러의 @Valid(MVC 인자 바인딩)가 트리거해야 동작하고,
        // 서비스 메서드를 직접 호출하는 이 테스트에서는 검증을 거치지 않는다. 그래서
        // signup()을 여기서 직접 호출하면 여전히 BCrypt의 raw 예외가 그대로 전파된다 —
        // 즉 실제 방어선은 AuthController의 @Valid이며, 이 클래스만으로는 500을 막지
        // 못한다는 걸 명시적으로 보여주는 테스트다.
        String oversized = "a1" + "b".repeat(71);
        SignupRequest request = new SignupRequest("20231234", "홍길동", oversized, oversized);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 bytes");
    }

    @Test
    void loginWithOversizedPasswordReturnsUnauthorizedNotServerError() {
        // #83 작업 4: matches()는 encode()와 달리 내부에서 IllegalArgumentException을
        // 삼키고 false를 반환하므로(BCryptPasswordEncoder 소스 확인 + 실측), 로그인 경로는
        // 500 위험 없이 정상적으로 401을 반환한다. 실제로 저장된 회원을 대상으로 검증한다.
        when(memberRepository.findByStudentNumber("20231234")).thenReturn(Optional.of(member(MemberStatus.APPROVED)));
        String oversizedPassword = "a1" + "b".repeat(71);

        assertBusinessException(
                () -> authService.login(new LoginRequest("20231234", oversizedPassword)),
                HttpStatus.UNAUTHORIZED
        );
    }

    @Test
    void approvedMemberCanLogin() {
        Member member = member(MemberStatus.APPROVED);
        when(memberRepository.findByStudentNumber("20231234")).thenReturn(Optional.of(member));

        assertThat(authService.login(new LoginRequest("20231234", "password1")).getStatus())
                .isEqualTo("APPROVED");
        assertThat(authService.login(new LoginRequest("20231234", "password1")).getAccessToken()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(value = MemberStatus.class, names = {"PENDING", "REJECTED", "WITHDRAWN"})
    void nonApprovedMemberCannotLogin(MemberStatus status) {
        when(memberRepository.findByStudentNumber("20231234")).thenReturn(Optional.of(member(status)));

        assertBusinessException(
                () -> authService.login(new LoginRequest("20231234", "password1")),
                HttpStatus.FORBIDDEN
        );
    }

    @Test
    void wrongPasswordCannotLogin() {
        when(memberRepository.findByStudentNumber("20231234")).thenReturn(Optional.of(member(MemberStatus.APPROVED)));

        assertBusinessException(
                () -> authService.login(new LoginRequest("20231234", "wrong-password")),
                HttpStatus.UNAUTHORIZED
        );
    }

    private Member member(MemberStatus status) {
        Member member = Member.createStudent("20231234", "홍길동", passwordEncoder.encode("password1"));
        if (status != MemberStatus.PENDING) {
            member.applyReviewResult(status);
        }
        return member;
    }

    private void assertBusinessException(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getStatus())
                .isEqualTo(status);
    }
}
