package com.sat.lms.admin.service;

import com.sat.lms.admin.dto.MemberApplicationResponse;
import com.sat.lms.member.entity.MemberStatus;
import com.sat.lms.member.repository.MemberRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MemberApplicationService {

    private final MemberRepository memberRepository;

    public MemberApplicationService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Page<MemberApplicationResponse> getMemberApplications(MemberStatus status, Pageable pageable) {
        return memberRepository.findByStatus(status, pageable)
                .map(MemberApplicationResponse::from);
    }
}