package com.sat.lms.attachment.entity;

import com.sat.lms.notice.entity.Notice;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "notice_attachment",
        uniqueConstraints = @UniqueConstraint(name = "uk_notice_attachment_notice_attachment",
                columnNames = {"notice_id", "attachment_id"}),
        indexes = {
                @Index(name = "idx_notice_attachment_notice_id", columnList = "notice_id"),
                @Index(name = "idx_notice_attachment_attachment_id", columnList = "attachment_id")
        })
public class NoticeAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notice_id", nullable = false)
    private Notice notice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attachment_id", nullable = false)
    private Attachment attachment;

    protected NoticeAttachment() {
    }

    public static NoticeAttachment create(Notice notice, Attachment attachment) {
        NoticeAttachment link = new NoticeAttachment();
        link.notice = notice;
        link.attachment = attachment;
        return link;
    }

    public Long getId() { return id; }
    public Notice getNotice() { return notice; }
    public Attachment getAttachment() { return attachment; }
}
