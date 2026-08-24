package com.sat.lms.assignment.entity;

import com.sat.lms.global.entity.BaseEntity;
import com.sat.lms.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "assignment", indexes = {
        @Index(name = "idx_assignment_admin_id", columnList = "admin_id"),
        @Index(name = "idx_assignment_due_at", columnList = "due_at"),
        @Index(name = "idx_assignment_created_at", columnList = "created_at")
})
public class Assignment extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Member admin;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "due_at", nullable = false)
    private OffsetDateTime dueAt;

    @Column(name = "allow_late_submission", nullable = false)
    private boolean allowLateSubmission;

    protected Assignment() {
    }

    public static Assignment create(Member admin, String title, String content, OffsetDateTime dueAt,
                                    boolean allowLateSubmission) {
        Assignment assignment = new Assignment();
        assignment.admin = admin;
        assignment.title = title;
        assignment.content = content;
        assignment.dueAt = dueAt;
        assignment.allowLateSubmission = allowLateSubmission;
        return assignment;
    }

    public void update(String title, String content, OffsetDateTime dueAt, Boolean allowLateSubmission) {
        if (title != null) this.title = title;
        if (content != null) this.content = content;
        if (dueAt != null) this.dueAt = dueAt;
        if (allowLateSubmission != null) this.allowLateSubmission = allowLateSubmission;
    }

    public Long getId() { return id; }
    public Member getAdmin() { return admin; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public boolean isAllowLateSubmission() { return allowLateSubmission; }
}
