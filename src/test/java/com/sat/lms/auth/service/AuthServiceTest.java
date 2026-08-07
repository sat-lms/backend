package com.sat.lms.auth.service;

import com.sat.lms.auth.dto.LoginRequest;
import com.sat.lms.auth.dto.SignupRequest;
import com.sat.lms.auth.dto.SignupResponse;
import com.sat.lms.global.exception.BusinessException;
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

import java.time.OffsetDateTime;
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
    private AuthService authService;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        authService = new AuthService(memberRepository, passwordEncoder);
    }

    @Test
    void signupSucceedsWithStudentPendingAndEncodedPassword() {
        SignupRequest request = new SignupRequest("20231234", " 홍길동 ", "password1", "password1");
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
    void approvedMemberCanLogin() {
        Member member = member(MemberStatus.APPROVED);
        when(memberRepository.findByStudentNumber("20231234")).thenReturn(Optional.of(member));

        assertThat(authService.login(new LoginRequest("20231234", "password1")).getStatus())
                .isEqualTo("APPROVED");
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
        OffsetDateTime now = OffsetDateTime.now();
        Member member = Member.createStudent("20231234", "홍길동", passwordEncoder.encode("password1"), now);
        if (status != MemberStatus.PENDING) {
            member.applyReviewResult(status, now);
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
