package com.sat.lms.assignment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.time.OffsetDateTime;

public class AssignmentUpdateRequest {
    private String title;
    private String content;
    private OffsetDateTime dueAt;
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
    public void setDueAt(OffsetDateTime dueAt) { this.dueAtPresent = true; this.dueAt = dueAt; }
    @JsonSetter("allowLateSubmission")
    public void setAllowLateSubmission(Boolean allowLateSubmission) {
        this.allowLateSubmissionPresent = true;
        this.allowLateSubmission = allowLateSubmission;
    }

    public String getTitle() { return title; }
    public String getContent() { return content; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public Boolean getAllowLateSubmission() { return allowLateSubmission; }
    @JsonIgnore public boolean isTitlePresent() { return titlePresent; }
    @JsonIgnore public boolean isContentPresent() { return contentPresent; }
    @JsonIgnore public boolean isDueAtPresent() { return dueAtPresent; }
    @JsonIgnore public boolean isAllowLateSubmissionPresent() { return allowLateSubmissionPresent; }
    @JsonIgnore public boolean isEmpty() {
        return !titlePresent && !contentPresent && !dueAtPresent && !allowLateSubmissionPresent;
    }
}
