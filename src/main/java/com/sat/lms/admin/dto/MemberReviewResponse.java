package com.sat.lms.admin.dto;

import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberReview;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "가입 신청 심사 응답")
public class MemberReviewResponse {

    @Schema(description = "회원 ID", example = "1")
    private final Long memberId;

    @Schema(description = "심사 후 계정 상태", example = "APPROVED")
    private final String status;

    @Schema(description = "심사자(관리자) 회원 ID", example = "2")
    private final Long reviewerId;

    @Schema(description = "거절 사유", example = "null")
    private final String rejectionReason;

    @Schema(description = "심사 처리 일시", example = "2026-08-07T02:00:00+09:00")
    private final OffsetDateTime reviewedAt;

    private MemberReviewResponse(Long memberId, String status, Long reviewerId, String rejectionReason, OffsetDateTime reviewedAt) {
        this.memberId = memberId;
        this.status = status;
        this.reviewerId = reviewerId;
        this.rejectionReason = rejectionReason;
        this.reviewedAt = reviewedAt;
    }

    public static MemberReviewResponse from(Member member, MemberReview review) {
        return new MemberReviewResponse(
                member.getId(),
                member.getStatus().name(),
                review.getReviewerId(),
                review.getRejectionReason(),
                review.getReviewedAt()
        );
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getStatus() {
        return status;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }
}