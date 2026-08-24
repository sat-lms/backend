package com.sat.lms.attachment.entity;

import com.sat.lms.submission.entity.Submission;
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
@Table(name = "submission_attachment",
        uniqueConstraints = @UniqueConstraint(name = "uk_submission_attachment_submission_attachment",
                columnNames = {"submission_id", "attachment_id"}),
        indexes = {
                @Index(name = "idx_submission_attachment_submission_id", columnList = "submission_id"),
                @Index(name = "idx_submission_attachment_attachment_id", columnList = "attachment_id")
        })
public class SubmissionAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attachment_id", nullable = false)
    private Attachment attachment;

    protected SubmissionAttachment() {
    }

    public static SubmissionAttachment create(Submission submission, Attachment attachment) {
        SubmissionAttachment link = new SubmissionAttachment();
        link.submission = submission;
        link.attachment = attachment;
        return link;
    }

    public Long getId() { return id; }
    public Submission getSubmission() { return submission; }
    public Attachment getAttachment() { return attachment; }
}
