package com.sat.lms.admin.service;

import com.sat.lms.admin.dto.AdminMemberDetailResponse;
import com.sat.lms.admin.dto.AdminMemberResponse;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.member.entity.InvalidMemberStateException;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberReview;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.entity.MemberStatus;
import com.sat.lms.member.repository.MemberRepository;
import com.sat.lms.member.repository.MemberReviewRepository;
import com.sat.lms.member.service.MemberGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminMemberService {
    private final MemberGuard memberGuard;
    private final MemberRepository memberRepository;
    private final MemberReviewRepository memberReviewRepository;

    public AdminMemberService(MemberGuard memberGuard, MemberRepository memberRepository,
                              MemberReviewRepository memberReviewRepository) {
        this.memberGuard = memberGuard;
        this.memberRepository = memberRepository;
        this.memberReviewRepository = memberReviewRepository;
    }

    @Transactional(readOnly = true)
    public Page<AdminMemberResponse> getMembers(Long adminId, MemberRole role, MemberStatus status,
                                                String keyword, Pageable pageable) {
        memberGuard.requireAdmin(adminId);
        return memberRepository.findMemberPage(role, status, keyword, pageable);
    }

    @Transactional(readOnly = true)
    public AdminMemberDetailResponse getMemberDetail(Long adminId, Long targetMemberId) {
        memberGuard.requireAdmin(adminId);
        Member member = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."));

        List<MemberReview> latestReviews = memberReviewRepository.findLatestByMemberId(targetMemberId,
                PageRequest.of(0, 1));
        MemberReview latestReview = latestReviews.isEmpty() ? null : latestReviews.get(0);
        String reviewerName = latestReview == null ? null
                : memberRepository.findById(latestReview.getReviewerId()).map(Member::getName).orElse(null);

        return AdminMemberDetailResponse.of(member, latestReview, reviewerName);
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
            target.expel();
        } catch (InvalidMemberStateException exception) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "탈퇴하거나 정지된 계정입니다.");
        }
        memberRepository.flush();
    }

    private BusinessException forbiddenTarget() {
        return new BusinessException(HttpStatus.FORBIDDEN, "학생 회원만 추방할 수 있습니다.");
    }
}
