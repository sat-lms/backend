package com.sat.lms.submission.dto;

import com.sat.lms.global.response.PageResponse;

public class AdminSubmissionSummaryResponse {
    private final long onTimeSubmittedCount;
    private final long lateSubmittedCount;
    private final long notSubmittedCount;
    private final PageResponse<AdminSubmissionStudentRow> students;

    public AdminSubmissionSummaryResponse(long onTimeSubmittedCount, long lateSubmittedCount, long notSubmittedCount,
                                          PageResponse<AdminSubmissionStudentRow> students) {
        this.onTimeSubmittedCount = onTimeSubmittedCount;
        this.lateSubmittedCount = lateSubmittedCount;
        this.notSubmittedCount = notSubmittedCount;
        this.students = students;
    }

    public long getOnTimeSubmittedCount() { return onTimeSubmittedCount; }
    public long getLateSubmittedCount() { return lateSubmittedCount; }
    public long getNotSubmittedCount() { return notSubmittedCount; }
    public PageResponse<AdminSubmissionStudentRow> getStudents() { return students; }
}
