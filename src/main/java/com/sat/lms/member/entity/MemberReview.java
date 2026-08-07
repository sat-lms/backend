package com.sat.lms.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "member_review")
public class MemberReview {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private MemberReviewAction action;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "reviewed_at", nullable = false)
    private OffsetDateTime reviewedAt;

    protected MemberReview() {
    }

    public MemberReview(Long memberId, Long reviewerId, MemberReviewAction action, String rejectionReason, OffsetDateTime reviewedAt) {
        this.memberId = memberId;
        this.reviewerId = reviewerId;
        this.action = action;
        this.rejectionReason = rejectionReason;
        this.reviewedAt = reviewedAt;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public MemberReviewAction getAction() {
        return action;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }
}