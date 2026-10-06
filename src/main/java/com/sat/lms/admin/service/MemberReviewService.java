package com.sat.lms.admin.service;

import com.sat.lms.admin.dto.MemberReviewRequest;
import com.sat.lms.admin.dto.MemberReviewResponse;
import com.sat.lms.admin.dto.BulkMemberApprovalRequest;
import com.sat.lms.admin.dto.BulkMemberApprovalResponse;
import com.sat.lms.admin.dto.BulkMemberApprovalResponse.Result;
import com.sat.lms.admin.dto.BulkMemberApprovalResponse.Outcome;
import com.sat.lms.admin.dto.BulkMemberApprovalResponse.Reason;
import com.sat.lms.member.service.MemberGuard;
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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@Transactional
public class MemberReviewService {

    private final MemberRepository memberRepository;
    private final MemberReviewRepository memberReviewRepository;
    private final MemberGuard memberGuard;

    public MemberReviewService(MemberRepository memberRepository, MemberReviewRepository memberReviewRepository,
                               MemberGuard memberGuard) {
        this.memberRepository = memberRepository;
        this.memberReviewRepository = memberReviewRepository;
        this.memberGuard = memberGuard;
    }

    public MemberReviewResponse review(Long memberId, MemberReviewRequest request, Long reviewerId) {
        validate(request);
        lockReviewer(reviewerId);

        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."));

        if (member.getStatus() != MemberStatus.PENDING) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 처리된 가입 신청입니다.");
        }

        MemberReview review = applyReview(memberId, member, reviewerId, request.getAction(), request.getRejectionReason());
        return MemberReviewResponse.from(member, review);
    }

    public BulkMemberApprovalResponse bulkApprove(BulkMemberApprovalRequest request, Long reviewerId) {
        lockReviewer(reviewerId);
        List<Long> ids = List.copyOf(new LinkedHashSet<>(request.memberIds()));
        var results = new LinkedHashMap<Long, Result>();
        // Lock order is independent of response order. The coordination lock also serializes
        // individual reviews, withdrawal, expulsion and reactivation before actor/target locks.
        for (Long id : ids.stream().sorted().toList()) {
            Member member = memberRepository.findByIdForUpdate(id).orElse(null);
            if (member == null) {
                results.put(id, new Result(id, Outcome.SKIPPED, Reason.NOT_FOUND));
            } else if (member.getStatus() != MemberStatus.PENDING) {
                results.put(id, new Result(id, Outcome.SKIPPED, Reason.NOT_PENDING));
            } else {
                applyReview(id, member, reviewerId, MemberReviewAction.APPROVED, null);
                results.put(id, new Result(id, Outcome.APPROVED, null));
            }
        }
        List<Result> ordered = ids.stream().map(results::get).toList();
        int approved = (int) ordered.stream().filter(result -> result.result() == Outcome.APPROVED).count();
        return new BulkMemberApprovalResponse(ids.size(), approved, ids.size() - approved, ordered);
    }

    private void lockReviewer(Long reviewerId) {
        memberRepository.findFirstByOrderByIdAsc();
        // First read of the actor occurs under its write lock, avoiding stale pre-lock state.
        memberGuard.requireAdminForUpdate(reviewerId);
    }

    private MemberReview applyReview(Long memberId, Member member, Long reviewerId,
                                     MemberReviewAction action, String reason) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean approved = action == MemberReviewAction.APPROVED;
        String rejectionReason = approved ? null : reason;

        MemberReview review = new MemberReview(memberId, reviewerId, action, rejectionReason, now);
        memberReviewRepository.save(review);

        member.applyReviewResult(approved ? MemberStatus.APPROVED : MemberStatus.REJECTED);

        return review;
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
