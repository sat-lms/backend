package com.sat.lms.member.entity;

import com.sat.lms.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "member")
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_number", nullable = false, unique = true, length = 10)
    private String studentNumber;

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MemberStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "deactivation_reason", length = 30)
    private MemberDeactivationReason deactivationReason;

    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    protected Member() {
    }

    public static Member createStudent(String studentNumber, String name, String passwordHash) {
        Member member = new Member();
        member.studentNumber = studentNumber;
        member.name = name;
        member.passwordHash = passwordHash;
        member.role = MemberRole.STUDENT;
        member.status = MemberStatus.PENDING;
        return member;
    }

    public void applyReviewResult(MemberStatus status) {
        this.status = status;
    }

    public void withdraw() {
        if (status != MemberStatus.APPROVED) {
            throw new InvalidMemberStateException("Only an approved member can withdraw");
        }
        this.status = MemberStatus.WITHDRAWN;
        this.deactivationReason = MemberDeactivationReason.SELF_WITHDRAWAL;
        this.tokenVersion = Math.addExact(this.tokenVersion, 1L);
    }

    public void expel() {
        if (status != MemberStatus.APPROVED || role != MemberRole.STUDENT) {
            throw new InvalidMemberStateException("Only an approved student can be expelled");
        }
        this.status = MemberStatus.WITHDRAWN;
        this.deactivationReason = MemberDeactivationReason.ADMIN_EXPULSION;
        this.tokenVersion = Math.addExact(this.tokenVersion, 1L);
    }

    public void requestReactivation() {
        if (status != MemberStatus.WITHDRAWN
                || deactivationReason != MemberDeactivationReason.SELF_WITHDRAWAL) {
            throw new InvalidMemberStateException("Only a self-withdrawn member can request reactivation");
        }
        this.status = MemberStatus.PENDING;
        this.deactivationReason = null;
    }

    public Long getId() {
        return id;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public String getName() {
        return name;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public MemberRole getRole() {
        return role;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public MemberDeactivationReason getDeactivationReason() {
        return deactivationReason;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

}
