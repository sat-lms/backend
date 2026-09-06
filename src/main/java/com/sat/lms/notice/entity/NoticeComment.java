package com.sat.lms.notice.entity;

import com.sat.lms.global.entity.BaseEntity;
import com.sat.lms.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "notice_comment",
        indexes = {
                @Index(name = "idx_notice_comment_notice_id", columnList = "notice_id"),
                @Index(name = "idx_notice_comment_author_id", columnList = "author_id")
        })
public class NoticeComment extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notice_id", nullable = false)
    private Notice notice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Member author;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    protected NoticeComment() {
    }

    public static NoticeComment create(Notice notice, Member author, String content) {
        NoticeComment comment = new NoticeComment();
        comment.notice = notice;
        comment.author = author;
        comment.content = content;
        return comment;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public Long getId() { return id; }
    public Notice getNotice() { return notice; }
    public Member getAuthor() { return author; }
    public String getContent() { return content; }
}
