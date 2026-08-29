package com.sat.lms.assignment.dto;

import com.sat.lms.assignment.entity.Assignment;
import com.sat.lms.attachment.entity.AssignmentAttachment;

import java.time.OffsetDateTime;
import java.util.List;

public class AssignmentDetailResponse {
    private final Long assignmentId;
    private final String title;
    private final String content;
    private final OffsetDateTime dueAt;
    private final boolean allowLateSubmission;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final List<AssignmentAttachmentDetailResponse> attachments;

    private AssignmentDetailResponse(Assignment assignment, List<AssignmentAttachment> attachments) {
        this.assignmentId = assignment.getId();
        this.title = assignment.getTitle();
        this.content = assignment.getContent();
        this.dueAt = assignment.getDueAt();
        this.allowLateSubmission = assignment.isAllowLateSubmission();
        this.createdAt = assignment.getCreatedAt();
        this.updatedAt = assignment.getUpdatedAt();
        this.attachments = attachments.stream()
                .map(link -> AssignmentAttachmentDetailResponse.from(link.getAttachment())).toList();
    }

    public static AssignmentDetailResponse from(Assignment assignment) {
        return new AssignmentDetailResponse(assignment, List.of());
    }

    public static AssignmentDetailResponse from(Assignment assignment,
                                                List<AssignmentAttachment> attachments) {
        return new AssignmentDetailResponse(assignment, attachments);
    }

    public Long getAssignmentId() { return assignmentId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public boolean getAllowLateSubmission() { return allowLateSubmission; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<AssignmentAttachmentDetailResponse> getAttachments() { return attachments; }
}
