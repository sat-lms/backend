package com.sat.lms.notice.dto;

import java.time.OffsetDateTime;

public class NoticeListResponse {
    private final Long noticeId;
    private final String title;
    private final boolean isPinned;
    private final OffsetDateTime createdAt;
    private final String authorName;
    private final boolean isRead;

    public NoticeListResponse(Long noticeId, String title, boolean isPinned, OffsetDateTime createdAt,
                              String authorName, boolean isRead) {
        this.noticeId = noticeId;
        this.title = title;
        this.isPinned = isPinned;
        this.createdAt = createdAt;
        this.authorName = authorName;
        this.isRead = isRead;
    }

    public Long getNoticeId() { return noticeId; }
    public String getTitle() { return title; }
    public boolean getIsPinned() { return isPinned; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public String getAuthorName() { return authorName; }
    public boolean getIsRead() { return isRead; }
}
