package com.sat.lms.admin.dto;

import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberReview;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "관리자 회원 상세 응답")
public class AdminMemberDetailResponse {

    @Schema(description = "회원 ID", example = "1")
    private final Long memberId;

    @Schema(description = "학번", example = "20231234")
    private final String studentNumber;

    @Schema(description = "이름", example = "최인준")
    private final String name;

    @Schema(description = "역할", example = "STUDENT")
    private final String role;

    @Schema(description = "계정 상태", example = "APPROVED")
    private final String status;

    @Schema(description = "가입일시", example = "2026-08-01T10:00:00+09:00")
    private final OffsetDateTime createdAt;

    @Schema(description = "가장 최근 심사 결과 (심사 이력이 없으면 null)", example = "APPROVED")
    private final String action;

    @Schema(description = "거절 사유 (승인이거나 심사 이력이 없으면 null)", example = "null")
    private final String rejectionReason;

    @Schema(description = "심사자 회원 ID (심사 이력이 없으면 null)", example = "2")
    private final Long reviewerId;

    @Schema(description = "심사자 이름 (심사 이력이 없으면 null)", example = "관리자1")
    private final String reviewerName;

    @Schema(description = "심사 처리 일시 (심사 이력이 없으면 null)", example = "2026-08-07T02:00:00+09:00")
    private final OffsetDateTime reviewedAt;

    private AdminMemberDetailResponse(Long memberId, String studentNumber, String name, String role, String status,
                                      OffsetDateTime createdAt, String action, String rejectionReason,
                                      Long reviewerId, String reviewerName, OffsetDateTime reviewedAt) {
        this.memberId = memberId;
        this.studentNumber = studentNumber;
        this.name = name;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
        this.action = action;
        this.rejectionReason = rejectionReason;
        this.reviewerId = reviewerId;
        this.reviewerName = reviewerName;
        this.reviewedAt = reviewedAt;
    }

    public static AdminMemberDetailResponse of(Member member, MemberReview latestReview, String reviewerName) {
        return new AdminMemberDetailResponse(
                member.getId(),
                member.getStudentNumber(),
                member.getName(),
                member.getRole().name(),
                member.getStatus().name(),
                member.getCreatedAt(),
                latestReview == null ? null : latestReview.getAction().name(),
                latestReview == null ? null : latestReview.getRejectionReason(),
                latestReview == null ? null : latestReview.getReviewerId(),
                latestReview == null ? null : reviewerName,
                latestReview == null ? null : latestReview.getReviewedAt());
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getAction() {
        return action;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }
}
