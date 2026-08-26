package com.sat.lms.submission.dto;

import com.sat.lms.attachment.entity.Attachment;
import com.sat.lms.submission.entity.Submission;

import java.time.OffsetDateTime;
import java.util.List;

public class SubmissionDetailResponse {
    private final Long submissionId;
    private final String textContent;
    private final boolean isLate;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final List<SubmissionFileResponse> files;

    private SubmissionDetailResponse(Submission submission, List<Attachment> attachments) {
        this.submissionId = submission.getId();
        this.textContent = submission.getTextContent();
        this.isLate = submission.isLate();
        this.createdAt = submission.getCreatedAt();
        this.updatedAt = submission.getUpdatedAt();
        this.files = attachments.stream().map(SubmissionFileResponse::from).toList();
    }

    public static SubmissionDetailResponse from(Submission submission, List<Attachment> attachments) {
        return new SubmissionDetailResponse(submission, attachments);
    }

    public Long getSubmissionId() { return submissionId; }
    public String getTextContent() { return textContent; }
    public boolean getIsLate() { return isLate; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<SubmissionFileResponse> getFiles() { return files; }
}