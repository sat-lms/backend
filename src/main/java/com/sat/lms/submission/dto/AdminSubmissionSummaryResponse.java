package com.sat.lms.submission.dto;

import com.sat.lms.global.response.PageResponse;

public class AdminSubmissionSummaryResponse {
    private final long submittedCount;
    private final long notSubmittedCount;
    /** submittedCount의 부분집합입니다(제출 중 지각 제출 건수). submittedCount + notSubmittedCount만 전체 학생 수와 일치합니다. */
    private final long lateCount;
    private final PageResponse<AdminSubmissionStudentRow> students;

    public AdminSubmissionSummaryResponse(long submittedCount, long notSubmittedCount, long lateCount,
                                          PageResponse<AdminSubmissionStudentRow> students) {
        this.submittedCount = submittedCount;
        this.notSubmittedCount = notSubmittedCount;
        this.lateCount = lateCount;
        this.students = students;
    }

    public long getSubmittedCount() { return submittedCount; }
    public long getNotSubmittedCount() { return notSubmittedCount; }

    /** submittedCount의 부분집합입니다(제출 중 지각 제출 건수). */
    public long getLateCount() { return lateCount; }

    public PageResponse<AdminSubmissionStudentRow> getStudents() { return students; }
}