package com.sat.lms.submission.dto;

public class AdminAssignmentSubmissionCounts {
    private final long submittedCount;
    private final long notSubmittedCount;
    /** submittedCount의 부분집합입니다(제출 중 지각 제출 건수). submittedCount + notSubmittedCount만 전체 학생 수와 일치합니다. */
    private final long lateCount;

    public AdminAssignmentSubmissionCounts(Long submittedCount, Long notSubmittedCount, Long lateCount) {
        this.submittedCount = submittedCount == null ? 0L : submittedCount;
        this.notSubmittedCount = notSubmittedCount == null ? 0L : notSubmittedCount;
        this.lateCount = lateCount == null ? 0L : lateCount;
    }

    public long getSubmittedCount() { return submittedCount; }
    public long getNotSubmittedCount() { return notSubmittedCount; }

    /** submittedCount의 부분집합입니다(제출 중 지각 제출 건수). */
    public long getLateCount() { return lateCount; }
}