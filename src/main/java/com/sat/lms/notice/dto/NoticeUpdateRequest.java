package com.sat.lms.notice.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

public class NoticeUpdateRequest {
    private String title;
    private String content;
    private Boolean isPinned;
    private boolean titlePresent;
    private boolean contentPresent;
    private boolean pinnedPresent;

    @JsonSetter("title")
    public void setTitle(String title) { this.titlePresent = true; this.title = title; }
    @JsonSetter("content")
    public void setContent(String content) { this.contentPresent = true; this.content = content; }
    @JsonSetter("isPinned")
    public void setIsPinned(Boolean isPinned) { this.pinnedPresent = true; this.isPinned = isPinned; }

    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Boolean getIsPinned() { return isPinned; }
    @JsonIgnore public boolean isTitlePresent() { return titlePresent; }
    @JsonIgnore public boolean isContentPresent() { return contentPresent; }
    @JsonIgnore public boolean isPinnedPresent() { return pinnedPresent; }
    @JsonIgnore public boolean isEmpty() { return !titlePresent && !contentPresent && !pinnedPresent; }
}
