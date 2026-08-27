package com.sat.lms.submission.dto;

import java.time.OffsetDateTime;

public class AdminSubmissionStudentRow {
    private final Long submissionId;
    private final String studentNumber;
    private final String studentName;
    private final OffsetDateTime submittedAt;
    private final boolean isLate;

    public AdminSubmissionStudentRow(Long submissionId, String studentNumber, String studentName,
                                     OffsetDateTime submittedAt, Boolean isLate) {
        this.submissionId = submissionId;
        this.studentNumber = studentNumber;
        this.studentName = studentName;
        this.submittedAt = submittedAt;
        this.isLate = Boolean.TRUE.equals(isLate);
    }

    public Long getSubmissionId() { return submissionId; }
    public String getStudentNumber() { return studentNumber; }
    public String getStudentName() { return studentName; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public boolean getIsLate() { return isLate; }
}