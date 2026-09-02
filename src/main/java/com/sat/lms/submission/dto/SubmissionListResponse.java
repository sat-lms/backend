package com.sat.lms.submission.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.OffsetDateTime;
import java.util.List;

public class SubmissionListResponse {
    private final Long submissionId;
    private final Long assignmentId;
    private final String assignmentTitle;
    private final OffsetDateTime dueAt;
    private final boolean allowLateSubmission;
    private final String textContent;
    private final boolean isLate;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final OffsetDateTime submittedAt;
    private SubmissionStatus submissionStatus;
    private List<SubmissionFileResponse> attachments = List.of();
    private List<String> fileNames = List.of();

    public SubmissionListResponse(Long submissionId, Long assignmentId, String assignmentTitle, String textContent,
                                  boolean isLate, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this(submissionId, assignmentId, assignmentTitle, null, false, textContent, isLate, createdAt, updatedAt, null);
    }

    public SubmissionListResponse(Long submissionId, Long assignmentId, String assignmentTitle, OffsetDateTime dueAt,
                                  boolean allowLateSubmission, String textContent, boolean isLate, OffsetDateTime createdAt,
                                  OffsetDateTime updatedAt, SubmissionStatus submissionStatus) {
        this.submissionId = submissionId;
        this.assignmentId = assignmentId;
        this.assignmentTitle = assignmentTitle;
        this.dueAt = dueAt;
        this.allowLateSubmission = allowLateSubmission;
        this.textContent = textContent;
        this.isLate = isLate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.submittedAt = updatedAt;
        this.submissionStatus = submissionStatus;
    }

    public SubmissionListResponse(Long submissionId, Long assignmentId, String assignmentTitle, OffsetDateTime dueAt,
                                  boolean allowLateSubmission, String textContent, boolean isLate, OffsetDateTime createdAt,
                                  OffsetDateTime updatedAt) {
        this(submissionId, assignmentId, assignmentTitle, dueAt, allowLateSubmission,
                textContent, isLate, createdAt, updatedAt, null);
    }

    public void assignAttachments(List<SubmissionFileResponse> attachments) {
        this.attachments = attachments;
        this.fileNames = attachments.stream().map(SubmissionFileResponse::getOriginalName).toList();
    }

    public void assignSubmissionStatus(SubmissionStatus submissionStatus) {
        this.submissionStatus = submissionStatus;
    }

    public Long getSubmissionId() { return submissionId; }
    public Long getAssignmentId() { return assignmentId; }
    public String getAssignmentTitle() { return assignmentTitle; }
    public OffsetDateTime getDueAt() { return dueAt; }
    @JsonIgnore
    public boolean isAllowLateSubmission() { return allowLateSubmission; }
    public String getTextContent() { return textContent; }
    public boolean getIsLate() { return isLate; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public SubmissionStatus getSubmissionStatus() { return submissionStatus; }
    public List<SubmissionFileResponse> getAttachments() { return attachments; }
    public List<String> getFileNames() { return fileNames; }
}
