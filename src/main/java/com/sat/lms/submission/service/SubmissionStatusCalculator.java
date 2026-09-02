package com.sat.lms.submission.service;

import com.sat.lms.submission.dto.SubmissionStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;

@Component
public class SubmissionStatusCalculator {
    public SubmissionStatus calculate(Long submissionId, boolean late, OffsetDateTime dueAt,
                                      boolean allowLateSubmission, Instant now) {
        if (submissionId != null) return late ? SubmissionStatus.LATE : SubmissionStatus.SUBMITTED;
        if (!now.isAfter(dueAt.toInstant()) || allowLateSubmission) return SubmissionStatus.IN_PROGRESS;
        return SubmissionStatus.NOT_SUBMITTED;
    }
}
