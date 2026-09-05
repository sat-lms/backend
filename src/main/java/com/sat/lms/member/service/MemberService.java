package com.sat.lms.member.service;

import com.sat.lms.member.dto.MemberMeResponse;
import com.sat.lms.member.dto.MemberWithdrawalRequest;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.entity.MemberStatus;
import com.sat.lms.member.repository.MemberRepository;
import com.sat.lms.global.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberGuard memberGuard;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberGuard memberGuard, MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberGuard = memberGuard;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public MemberMeResponse getMe(Long memberId) {
        Member member = memberGuard.requireMember(memberId);
        return MemberMeResponse.from(member);
    }

    @Transactional
    public void withdraw(Long memberId, MemberWithdrawalRequest request) {
        memberRepository.findFirstByOrderByIdAsc();
        Member member = memberGuard.requireMemberForUpdate(memberId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), member.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 올바르지 않습니다.");
        }
        if (member.getRole() == MemberRole.ADMIN
                && memberRepository.countByRoleAndStatus(MemberRole.ADMIN, MemberStatus.APPROVED) <= 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "마지막 관리자는 회원탈퇴할 수 없습니다.");
        }
        member.withdraw();
        memberRepository.flush();
    }
}
