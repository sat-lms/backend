package com.sat.lms.assignment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.OptBoolean;

import java.time.LocalDateTime;

public class AssignmentUpdateRequest {
    private String title;
    private String content;
    @JsonFormat(pattern = "uuuu-MM-dd'T'HH:mm:ss", lenient = OptBoolean.FALSE)
    private LocalDateTime dueAt;
    private Boolean allowLateSubmission;
    private boolean titlePresent;
    private boolean contentPresent;
    private boolean dueAtPresent;
    private boolean allowLateSubmissionPresent;

    @JsonSetter("title")
    public void setTitle(String title) { this.titlePresent = true; this.title = title; }
    @JsonSetter("content")
    public void setContent(String content) { this.contentPresent = true; this.content = content; }
    @JsonSetter("dueAt")
    public void setDueAt(LocalDateTime dueAt) { this.dueAtPresent = true; this.dueAt = dueAt; }
    @JsonSetter("allowLateSubmission")
    public void setAllowLateSubmission(Boolean allowLateSubmission) {
        this.allowLateSubmissionPresent = true;
        this.allowLateSubmission = allowLateSubmission;
    }

    public String getTitle() { return title; }
    public String getContent() { return content; }
    public LocalDateTime getDueAt() { return dueAt; }
    public Boolean getAllowLateSubmission() { return allowLateSubmission; }
    @JsonIgnore public boolean isTitlePresent() { return titlePresent; }
    @JsonIgnore public boolean isContentPresent() { return contentPresent; }
    @JsonIgnore public boolean isDueAtPresent() { return dueAtPresent; }
    @JsonIgnore public boolean isAllowLateSubmissionPresent() { return allowLateSubmissionPresent; }
    @JsonIgnore public boolean isEmpty() {
        return !titlePresent && !contentPresent && !dueAtPresent && !allowLateSubmissionPresent;
    }
}
