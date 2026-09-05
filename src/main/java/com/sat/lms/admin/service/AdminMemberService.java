package com.sat.lms.admin.service;

import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.member.entity.InvalidMemberStateException;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.repository.MemberRepository;
import com.sat.lms.member.service.MemberGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminMemberService {
    private final MemberGuard memberGuard;
    private final MemberRepository memberRepository;

    public AdminMemberService(MemberGuard memberGuard, MemberRepository memberRepository) {
        this.memberGuard = memberGuard;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public void expel(Long adminId, Long targetMemberId) {
        memberGuard.requireAdmin(adminId);
        if (adminId.equals(targetMemberId)) {
            throw forbiddenTarget();
        }

        // Keep the same first lock as voluntary withdrawal. The returned member is intentionally unused;
        // this serializes both flows before either locks an actor or target member row.
        memberRepository.findFirstByOrderByIdAsc();
        memberGuard.requireAdminForUpdate(adminId);
        Member target = memberGuard.requireMemberForUpdate(targetMemberId);
        if (target.getRole() != MemberRole.STUDENT) {
            throw forbiddenTarget();
        }
        try {
            target.withdraw();
        } catch (InvalidMemberStateException exception) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "탈퇴하거나 정지된 계정입니다.");
        }
        memberRepository.flush();
    }

    private BusinessException forbiddenTarget() {
        return new BusinessException(HttpStatus.FORBIDDEN, "학생 회원만 추방할 수 있습니다.");
    }
}
