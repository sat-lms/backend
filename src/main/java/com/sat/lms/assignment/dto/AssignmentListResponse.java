package com.sat.lms.assignment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sat.lms.submission.dto.SubmissionStatus;
import java.time.OffsetDateTime;

public class AssignmentListResponse {
    private final Long assignmentId;
    private final String title;
    private final OffsetDateTime dueAt;
    private final boolean allowLateSubmission;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final Long submissionId;
    private final boolean submissionLate;
    private SubmissionStatus submissionStatus;

    public AssignmentListResponse(Long assignmentId, String title, OffsetDateTime dueAt,
                                  boolean allowLateSubmission, OffsetDateTime createdAt,
                                  OffsetDateTime updatedAt) {
        this(assignmentId, title, dueAt, allowLateSubmission, createdAt, updatedAt, null, false);
    }

    public AssignmentListResponse(Long assignmentId, String title, OffsetDateTime dueAt,
                                  boolean allowLateSubmission, OffsetDateTime createdAt,
                                  OffsetDateTime updatedAt, Long submissionId, boolean submissionLate) {
        this.assignmentId = assignmentId;
        this.title = title;
        this.dueAt = dueAt;
        this.allowLateSubmission = allowLateSubmission;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.submissionId = submissionId;
        this.submissionLate = submissionLate;
    }

    public Long getAssignmentId() { return assignmentId; }
    public String getTitle() { return title; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public boolean getAllowLateSubmission() { return allowLateSubmission; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public SubmissionStatus getSubmissionStatus() { return submissionStatus; }
    public void assignSubmissionStatus(SubmissionStatus status) { this.submissionStatus = status; }
    @JsonIgnore public Long getSubmissionIdForStatus() { return submissionId; }
    @JsonIgnore public boolean isSubmissionLateForStatus() { return submissionLate; }
}
