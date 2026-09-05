package com.sat.lms.member.service;

import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.entity.MemberStatus;
import com.sat.lms.member.repository.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 요청 처리 경로에서 회원 존재·상태·역할을 검증하는 공통 가드.
 * 로그인 시점 검증(AuthService)과 별개로, 로그인 이후 매 요청에 대한 두 번째 방어선이다.
 */
@Service
public class MemberGuard {
    private final MemberRepository memberRepository;

    public MemberGuard(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Member requireMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."));
        requireApproved(member);
        return member;
    }

    public Member requireMemberForUpdate(Long memberId) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."));
        requireApproved(member);
        return member;
    }

    private void requireApproved(Member member) {
        if (member.getStatus() != MemberStatus.APPROVED) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "탈퇴하거나 정지된 계정입니다.");
        }
    }

    public Member requireAdmin(Long memberId) {
        Member member = requireMember(memberId);
        if (member.getRole() != MemberRole.ADMIN) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
        return member;
    }

    public Member requireStudent(Long memberId) {
        Member member = requireMember(memberId);
        if (member.getRole() != MemberRole.STUDENT) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "학생만 이용할 수 있는 기능입니다.");
        }
        return member;
    }
}
