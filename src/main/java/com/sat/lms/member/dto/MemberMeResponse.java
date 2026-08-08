package com.sat.lms.member.dto;

import com.sat.lms.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "내 회원 정보 응답")
public class MemberMeResponse {

    private final Long id;
    private final String studentNumber;
    private final String name;
    private final String role;
    private final String status;
    private final OffsetDateTime createdAt;

    private MemberMeResponse(Long id, String studentNumber, String name, String role, String status, OffsetDateTime createdAt) {
        this.id = id;
        this.studentNumber = studentNumber;
        this.name = name;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static MemberMeResponse from(Member member) {
        return new MemberMeResponse(
                member.getId(), member.getStudentNumber(), member.getName(), member.getRole().name(),
                member.getStatus().name(), member.getCreatedAt()
        );
    }

    public Long getId() { return id; }
    public String getStudentNumber() { return studentNumber; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
