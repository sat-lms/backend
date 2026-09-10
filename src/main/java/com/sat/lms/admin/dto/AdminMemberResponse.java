package com.sat.lms.admin.dto;

import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.entity.MemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "관리자 회원 목록 항목 응답")
public class AdminMemberResponse {

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

    public AdminMemberResponse(Long memberId, String studentNumber, String name, MemberRole role,
                               MemberStatus status, OffsetDateTime createdAt) {
        this.memberId = memberId;
        this.studentNumber = studentNumber;
        this.name = name;
        this.role = role.name();
        this.status = status.name();
        this.createdAt = createdAt;
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
}
