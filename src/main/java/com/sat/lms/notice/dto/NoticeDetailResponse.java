package com.sat.lms.notice.dto;

import com.sat.lms.notice.entity.Notice;
import com.sat.lms.attachment.entity.NoticeAttachment;

import java.time.OffsetDateTime;
import java.util.List;

public class NoticeDetailResponse {
    private final Long noticeId;
    private final String title;
    private final String content;
    private final boolean isPinned;
    private final String authorName;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
    private final boolean isRead;
    private final List<NoticeAttachmentResponse> attachments;

    private NoticeDetailResponse(Notice notice, boolean isRead, List<NoticeAttachment> attachments) {
        this.noticeId = notice.getId();
        this.title = notice.getTitle();
        this.content = notice.getContent();
        this.isPinned = notice.isPinned();
        this.authorName = notice.getAdmin().getName();
        this.createdAt = notice.getCreatedAt();
        this.updatedAt = notice.getUpdatedAt();
        this.isRead = isRead;
        this.attachments = attachments.stream()
                .map(link -> NoticeAttachmentResponse.from(link.getAttachment()))
                .toList();
    }

    public static NoticeDetailResponse from(Notice notice, boolean isRead) {
        return new NoticeDetailResponse(notice, isRead, List.of());
    }

    public static NoticeDetailResponse from(Notice notice, boolean isRead,
                                            List<NoticeAttachment> attachments) {
        return new NoticeDetailResponse(notice, isRead, attachments);
    }
    public Long getNoticeId() { return noticeId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public boolean getIsPinned() { return isPinned; }
    public String getAuthorName() { return authorName; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public boolean getIsRead() { return isRead; }
    public List<NoticeAttachmentResponse> getAttachments() { return attachments; }
}
