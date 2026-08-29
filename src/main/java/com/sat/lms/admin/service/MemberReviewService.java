package com.sat.lms.admin.service;

import com.sat.lms.admin.dto.MemberReviewRequest;
import com.sat.lms.admin.dto.MemberReviewResponse;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberReview;
import com.sat.lms.member.entity.MemberReviewAction;
import com.sat.lms.member.entity.MemberStatus;
import com.sat.lms.member.repository.MemberRepository;
import com.sat.lms.member.repository.MemberReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@Transactional
public class MemberReviewService {

    private final MemberRepository memberRepository;
    private final MemberReviewRepository memberReviewRepository;

    public MemberReviewService(MemberRepository memberRepository, MemberReviewRepository memberReviewRepository) {
        this.memberRepository = memberRepository;
        this.memberReviewRepository = memberReviewRepository;
    }

    public MemberReviewResponse review(Long memberId, MemberReviewRequest request, Long reviewerId) {
        validate(request);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."));

        if (member.getStatus() != MemberStatus.PENDING) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 처리된 가입 신청입니다.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean approved = request.getAction() == MemberReviewAction.APPROVED;
        String rejectionReason = approved ? null : request.getRejectionReason();

        MemberReview review = new MemberReview(memberId, reviewerId, request.getAction(), rejectionReason, now);
        memberReviewRepository.save(review);

        member.applyReviewResult(approved ? MemberStatus.APPROVED : MemberStatus.REJECTED);

        return MemberReviewResponse.from(member, review);
    }

    private void validate(MemberReviewRequest request) {
        if (request.getAction() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "action은 필수입니다.");
        }
        if (request.getAction() == MemberReviewAction.REJECTED
                && (request.getRejectionReason() == null || request.getRejectionReason().isBlank())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "거절 시 rejectionReason은 필수입니다.");
        }
    }
}
