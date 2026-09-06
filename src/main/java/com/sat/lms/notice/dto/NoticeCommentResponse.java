package com.sat.lms.notice.dto;

import com.sat.lms.notice.entity.NoticeComment;

import java.time.OffsetDateTime;

public class NoticeCommentResponse {
    private final Long commentId;
    private final String content;
    private final String authorName;
    private final String authorRole;
    private final OffsetDateTime createdAt;

    private NoticeCommentResponse(Long commentId, String content, String authorName, String authorRole,
                                  OffsetDateTime createdAt) {
        this.commentId = commentId;
        this.content = content;
        this.authorName = authorName;
        this.authorRole = authorRole;
        this.createdAt = createdAt;
    }

    public static NoticeCommentResponse from(NoticeComment comment) {
        return new NoticeCommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getAuthor().getName(),
                comment.getAuthor().getRole().name(),
                comment.getCreatedAt());
    }

    public Long getCommentId() { return commentId; }
    public String getContent() { return content; }
    public String getAuthorName() { return authorName; }
    public String getAuthorRole() { return authorRole; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
