package com.sat.lms.admin.dto;

import com.sat.lms.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "가입 신청 목록 항목 응답")
public class MemberApplicationResponse {

    @Schema(description = "회원 ID", example = "1")
    private final Long memberId;

    @Schema(description = "학번", example = "20231234")
    private final String studentNumber;

    @Schema(description = "이름", example = "최인준")
    private final String name;

    @Schema(description = "계정 상태", example = "PENDING")
    private final String status;

    @Schema(description = "가입 신청 일시", example = "2026-08-01T10:00:00+09:00")
    private final OffsetDateTime createdAt;

    public MemberApplicationResponse(Long memberId, String studentNumber, String name, String status, OffsetDateTime createdAt) {
        this.memberId = memberId;
        this.studentNumber = studentNumber;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static MemberApplicationResponse from(Member member) {
        return new MemberApplicationResponse(
                member.getId(),
                member.getStudentNumber(),
                member.getName(),
                member.getStatus().name(),
                member.getCreatedAt()
        );
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

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}