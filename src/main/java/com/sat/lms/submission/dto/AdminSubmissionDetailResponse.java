package com.sat.lms.submission.dto;

import com.sat.lms.attachment.entity.Attachment;
import com.sat.lms.submission.entity.Submission;

import java.time.OffsetDateTime;
import java.util.List;

public class AdminSubmissionDetailResponse {
    private final Long submissionId;
    private final Long assignmentId;
    private final String assignmentTitle;
    private final String studentNumber;
    private final String studentName;
    private final String textContent;
    private final boolean isLate;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final List<SubmissionFileResponse> files;

    private AdminSubmissionDetailResponse(Submission submission, List<Attachment> attachments) {
        this.submissionId = submission.getId();
        this.assignmentId = submission.getAssignment().getId();
        this.assignmentTitle = submission.getAssignment().getTitle();
        this.studentNumber = submission.getStudent().getStudentNumber();
        this.studentName = submission.getStudent().getName();
        this.textContent = submission.getTextContent();
        this.isLate = submission.isLate();
        this.createdAt = submission.getCreatedAt();
        this.updatedAt = submission.getUpdatedAt();
        this.files = attachments.stream().map(SubmissionFileResponse::from).toList();
    }

    public static AdminSubmissionDetailResponse from(Submission submission, List<Attachment> attachments) {
        return new AdminSubmissionDetailResponse(submission, attachments);
    }

    public Long getSubmissionId() { return submissionId; }
    public Long getAssignmentId() { return assignmentId; }
    public String getAssignmentTitle() { return assignmentTitle; }
    public String getStudentNumber() { return studentNumber; }
    public String getStudentName() { return studentName; }
    public String getTextContent() { return textContent; }
    public boolean getIsLate() { return isLate; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<SubmissionFileResponse> getFiles() { return files; }
}