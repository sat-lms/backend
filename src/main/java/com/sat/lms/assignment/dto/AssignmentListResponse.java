package com.sat.lms.assignment.dto;

import java.time.OffsetDateTime;

public class AssignmentListResponse {
    private final Long assignmentId;
    private final String title;
    private final OffsetDateTime dueAt;
    private final boolean allowLateSubmission;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public AssignmentListResponse(Long assignmentId, String title, OffsetDateTime dueAt,
                                  boolean allowLateSubmission, OffsetDateTime createdAt,
                                  OffsetDateTime updatedAt) {
        this.assignmentId = assignmentId;
        this.title = title;
        this.dueAt = dueAt;
        this.allowLateSubmission = allowLateSubmission;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getAssignmentId() { return assignmentId; }
    public String getTitle() { return title; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public boolean getAllowLateSubmission() { return allowLateSubmission; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
