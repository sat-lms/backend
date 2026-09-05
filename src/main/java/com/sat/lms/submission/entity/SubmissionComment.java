package com.sat.lms.submission.entity;

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

@Entity
@Table(name = "submission_comment",
        indexes = {
                @Index(name = "idx_submission_comment_submission_id", columnList = "submission_id"),
                @Index(name = "idx_submission_comment_author_id", columnList = "author_id")
        })
public class SubmissionComment extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Member author;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    protected SubmissionComment() {
    }

    public static SubmissionComment create(Submission submission, Member author, String content) {
        SubmissionComment comment = new SubmissionComment();
        comment.submission = submission;
        comment.author = author;
        comment.content = content;
        return comment;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public Long getId() { return id; }
    public Submission getSubmission() { return submission; }
    public Member getAuthor() { return author; }
    public String getContent() { return content; }
}
