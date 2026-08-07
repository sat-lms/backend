package com.sat.lms.member.service;

import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.member.dto.MemberMeResponse;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.repository.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public MemberMeResponse getMe(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."));
        return MemberMeResponse.from(member);
    }
}
