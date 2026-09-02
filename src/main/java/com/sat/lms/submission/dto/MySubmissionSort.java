package com.sat.lms.submission.dto;

public enum MySubmissionSort {
    DUE_AT_DESC("dueAtDesc"),
    DUE_AT_ASC("dueAtAsc"),
    SUBMITTED_AT_DESC("submittedAtDesc");

    private final String parameterValue;

    MySubmissionSort(String parameterValue) {
        this.parameterValue = parameterValue;
    }

    public static MySubmissionSort from(String value) {
        for (MySubmissionSort sort : values()) {
            if (sort.parameterValue.equals(value)) return sort;
        }
        throw new IllegalArgumentException("Unsupported submission sort");
    }
}
