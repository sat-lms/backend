package com.sat.lms.submission.dto;

import java.time.OffsetDateTime;
import java.util.List;

public class SubmissionListResponse {
    private final Long submissionId;
    private final Long assignmentId;
    private final String assignmentTitle;
    private final String textContent;
    private final boolean isLate;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private List<SubmissionFileResponse> attachments = List.of();

    public SubmissionListResponse(Long submissionId, Long assignmentId, String assignmentTitle, String textContent,
                                  boolean isLate, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.submissionId = submissionId;
        this.assignmentId = assignmentId;
        this.assignmentTitle = assignmentTitle;
        this.textContent = textContent;
        this.isLate = isLate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void assignAttachments(List<SubmissionFileResponse> attachments) {
        this.attachments = attachments;
    }

    public Long getSubmissionId() { return submissionId; }
    public Long getAssignmentId() { return assignmentId; }
    public String getAssignmentTitle() { return assignmentTitle; }
    public String getTextContent() { return textContent; }
    public boolean getIsLate() { return isLate; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<SubmissionFileResponse> getAttachments() { return attachments; }
}
