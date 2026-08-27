package com.sat.lms.assignment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public class AssignmentCreateRequest {
    @NotBlank(message = "제목은 필수입니다.")
    private String title;

    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    @NotNull(message = "마감 시각은 필수입니다.")
    private OffsetDateTime dueAt;

    @NotNull(message = "지각 제출 허용 여부는 필수입니다.")
    private Boolean allowLateSubmission;

    public String getTitle() { return title; }
    public String getContent() { return content; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public Boolean getAllowLateSubmission() { return allowLateSubmission; }
}
