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
@Table(name = "notice", indexes = {
        @Index(name = "idx_notice_admin_id", columnList = "admin_id"),
        @Index(name = "idx_notice_created_at", columnList = "created_at"),
        @Index(name = "idx_notice_pinned_created_at", columnList = "is_pinned, created_at")
})
public class Notice extends BaseEntity {
    public static final int TITLE_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Member admin;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_pinned", nullable = false)
    private boolean pinned;

    protected Notice() {
    }

    private Notice(Member admin, String title, String content, boolean pinned) {
        this.admin = admin;
        this.title = title;
        this.content = content;
        this.pinned = pinned;
    }

    public static Notice create(Member admin, String title, String content, boolean pinned) {
        return new Notice(admin, title, content, pinned);
    }

    public void update(String title, String content, Boolean pinned) {
        if (title != null) this.title = title;
        if (content != null) this.content = content;
        if (pinned != null) this.pinned = pinned;
    }

    public Long getId() { return id; }
    public Member getAdmin() { return admin; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public boolean isPinned() { return pinned; }
}
