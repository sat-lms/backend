package com.sat.lms.auth.service;

import com.sat.lms.auth.dto.LoginRequest;
import com.sat.lms.auth.dto.LoginResponse;
import com.sat.lms.auth.dto.SignupRequest;
import com.sat.lms.auth.dto.SignupResponse;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.security.JwtTokenProvider;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberStatus;
import com.sat.lms.member.repository.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "비밀번호와 비밀번호 확인이 일치하지 않습니다.");
        }
        if (memberRepository.existsByStudentNumber(request.getStudentNumber())) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 사용 중인 학번입니다.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        Member member = Member.createStudent(
                request.getStudentNumber(),
                request.getName().trim(),
                passwordEncoder.encode(request.getPassword()),
                now
        );
        Member saved = memberRepository.save(member);
        return new SignupResponse(saved.getId(), saved.getStudentNumber(), saved.getName(), saved.getStatus().name(), saved.getCreatedAt());
    }

    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByStudentNumber(request.getStudentNumber())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "학번 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), member.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "학번 또는 비밀번호가 올바르지 않습니다.");
        }
        if (member.getStatus() != MemberStatus.APPROVED) {
            throw loginBlocked(member.getStatus());
        }

        String accessToken = jwtTokenProvider.createAccessToken(member.getId(), member.getRole().name());
        return new LoginResponse(member.getId(), member.getStudentNumber(), member.getName(),
                member.getRole().name(), member.getStatus().name(), accessToken, "Bearer",
                jwtTokenProvider.getExpirationSeconds());
    }

    private BusinessException loginBlocked(MemberStatus status) {
        String message = switch (status) {
            case PENDING -> "승인 대기 중인 회원입니다.";
            case REJECTED -> "가입 신청이 거절된 회원입니다.";
            case WITHDRAWN -> "탈퇴한 회원입니다.";
            case APPROVED -> "로그인할 수 없는 회원 상태입니다.";
        };
        return new BusinessException(HttpStatus.FORBIDDEN, message);
    }
}
