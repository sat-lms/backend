package com.sat.lms.submission.dto;

public class AdminAssignmentSubmissionCounts {
    private final long onTimeSubmittedCount;
    private final long lateSubmittedCount;
    private final long notSubmittedCount;

    public AdminAssignmentSubmissionCounts(Long onTimeSubmittedCount, Long lateSubmittedCount,
                                           Long notSubmittedCount) {
        this.onTimeSubmittedCount = onTimeSubmittedCount == null ? 0L : onTimeSubmittedCount;
        this.lateSubmittedCount = lateSubmittedCount == null ? 0L : lateSubmittedCount;
        this.notSubmittedCount = notSubmittedCount == null ? 0L : notSubmittedCount;
    }

    public long getOnTimeSubmittedCount() { return onTimeSubmittedCount; }
    public long getLateSubmittedCount() { return lateSubmittedCount; }
    public long getNotSubmittedCount() { return notSubmittedCount; }
}
