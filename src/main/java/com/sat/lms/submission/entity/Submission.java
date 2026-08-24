package com.sat.lms.submission.entity;

import com.sat.lms.assignment.entity.Assignment;
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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "submission",
        uniqueConstraints = @UniqueConstraint(name = "uk_submission_assignment_student",
                columnNames = {"assignment_id", "student_id"}),
        indexes = {
                @Index(name = "idx_submission_assignment_id", columnList = "assignment_id"),
                @Index(name = "idx_submission_student_id", columnList = "student_id"),
                @Index(name = "idx_submission_created_at", columnList = "created_at")
        })
public class Submission extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Member student;

    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    @Column(name = "is_late", nullable = false)
    private boolean late;

    protected Submission() {
    }

    public static Submission create(Assignment assignment, Member student, String textContent, boolean late) {
        Submission submission = new Submission();
        submission.assignment = assignment;
        submission.student = student;
        submission.textContent = textContent;
        submission.late = late;
        return submission;
    }

    public void updateTextContent(String textContent) { this.textContent = textContent; }

    public Long getId() { return id; }
    public Assignment getAssignment() { return assignment; }
    public Member getStudent() { return student; }
    public String getTextContent() { return textContent; }
    public boolean isLate() { return late; }
}
