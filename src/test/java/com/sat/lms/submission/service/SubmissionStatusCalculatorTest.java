package com.sat.lms.submission.service;

import com.sat.lms.submission.dto.SubmissionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionStatusCalculatorTest {
    private final SubmissionStatusCalculator calculator = new SubmissionStatusCalculator();
    private final Instant now = Instant.parse("2026-09-02T00:00:00Z");

    @Test
    void existingSubmissionAlwaysUsesPersistedLateFlag() {
        assertThat(calculate(1L, false, now.minusSeconds(1), false)).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(calculate(1L, true, now.plusSeconds(1), false)).isEqualTo(SubmissionStatus.LATE);
    }

    @Test
    void missingSubmissionUsesEditableDeadlinePolicyIncludingEqualInstantAcrossOffsets() {
        assertThat(calculate(null, false, now.plusSeconds(1), false)).isEqualTo(SubmissionStatus.IN_PROGRESS);
        assertThat(calculate(null, false, now, false)).isEqualTo(SubmissionStatus.IN_PROGRESS);
        OffsetDateTime sameInstantDifferentOffset = OffsetDateTime.ofInstant(now, ZoneOffset.ofHours(9));
        assertThat(calculator.calculate(null, false, sameInstantDifferentOffset, false, now))
                .isEqualTo(SubmissionStatus.IN_PROGRESS);
        assertThat(calculate(null, false, now.minusSeconds(1), true)).isEqualTo(SubmissionStatus.IN_PROGRESS);
        assertThat(calculate(null, false, now.minusSeconds(1), false)).isEqualTo(SubmissionStatus.NOT_SUBMITTED);
    }

    private SubmissionStatus calculate(Long submissionId, boolean late, Instant dueAt, boolean allowLate) {
        return calculator.calculate(submissionId, late,
                OffsetDateTime.ofInstant(dueAt, ZoneOffset.UTC), allowLate, now);
    }
}
