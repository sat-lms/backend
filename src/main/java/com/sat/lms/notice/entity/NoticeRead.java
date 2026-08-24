package com.sat.lms.notice.entity;

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
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notice_read",
        uniqueConstraints = @UniqueConstraint(name = "uk_notice_read_notice_member",
                columnNames = {"notice_id", "member_id"}),
        indexes = {
                @Index(name = "idx_notice_read_notice_id", columnList = "notice_id"),
                @Index(name = "idx_notice_read_member_id", columnList = "member_id")
        })
public class NoticeRead {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notice_id", nullable = false)
    private Notice notice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "read_at", nullable = false)
    private OffsetDateTime readAt;

    protected NoticeRead() {
    }

    public static NoticeRead create(Notice notice, Member member, OffsetDateTime readAt) {
        NoticeRead noticeRead = new NoticeRead();
        noticeRead.notice = notice;
        noticeRead.member = member;
        noticeRead.readAt = readAt;
        return noticeRead;
    }

    public Long getId() { return id; }
    public Notice getNotice() { return notice; }
    public Member getMember() { return member; }
    public OffsetDateTime getReadAt() { return readAt; }
}
