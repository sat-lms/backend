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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "학번 또는 비밀번호가 올바르지 않습니다.";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final String loginDummyPasswordHash;

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       @Qualifier("loginDummyPasswordHash") String loginDummyPasswordHash) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginDummyPasswordHash = loginDummyPasswordHash;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "비밀번호와 비밀번호 확인이 일치하지 않습니다.");
        }
        if (memberRepository.existsByStudentNumber(request.getStudentNumber())) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 사용 중인 학번입니다.");
        }

        Member member = Member.createStudent(
                request.getStudentNumber(),
                request.getName().trim(),
                passwordEncoder.encode(request.getPassword())
        );
        Member saved = memberRepository.save(member);
        return new SignupResponse(saved.getId(), saved.getStudentNumber(), saved.getName(), saved.getStatus().name(), saved.getCreatedAt());
    }

    public LoginResponse login(LoginRequest request) {
        var member = memberRepository.findByStudentNumber(request.getStudentNumber());
        String passwordHash = member.map(Member::getPasswordHash).orElse(loginDummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), passwordHash);

        if (member.isEmpty() || !passwordMatches || member.get().getStatus() != MemberStatus.APPROVED) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
        }

        Member approvedMember = member.get();
        String accessToken = jwtTokenProvider.createAccessToken(approvedMember.getId(), approvedMember.getRole().name());
        return new LoginResponse(approvedMember.getId(), approvedMember.getStudentNumber(), approvedMember.getName(),
                approvedMember.getRole().name(), approvedMember.getStatus().name(), accessToken, "Bearer",
                jwtTokenProvider.getExpirationSeconds());
    }
}
