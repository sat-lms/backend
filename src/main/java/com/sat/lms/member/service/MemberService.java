package com.sat.lms.member.service;

import com.sat.lms.member.dto.MemberMeResponse;
import com.sat.lms.member.entity.Member;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberGuard memberGuard;

    public MemberService(MemberGuard memberGuard) {
        this.memberGuard = memberGuard;
    }

    public MemberMeResponse getMe(Long memberId) {
        Member member = memberGuard.requireMember(memberId);
        return MemberMeResponse.from(member);
    }
}
