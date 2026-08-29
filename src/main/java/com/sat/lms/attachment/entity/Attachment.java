package com.sat.lms.attachment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

// attachment 테이블(V6 마이그레이션)에는 updated_at 컬럼이 없어 BaseEntity를 상속하지 않습니다.
// BaseEntity로 바꾸면 매핑된 updated_at 컬럼이 실제 스키마에 없어 검증 오류가 발생합니다.
@Entity
@Table(name = "attachment", uniqueConstraints =
        @UniqueConstraint(name = "uk_attachment_storage_key", columnNames = "storage_key"))
@EntityListeners(AuditingEntityListener.class)
public class Attachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_name", nullable = false, columnDefinition = "TEXT")
    private String originalName;

    @Column(name = "stored_name", nullable = false, columnDefinition = "TEXT")
    private String storedName;

    @Column(name = "storage_key", nullable = false, columnDefinition = "TEXT")
    private String storageKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String extension;

    @Column(name = "size_kb", nullable = false)
    private Long sizeKb;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Attachment() {
    }

    public static Attachment create(String originalName, String storedName, String storageKey,
                                    String extension, Long sizeKb) {
        Attachment attachment = new Attachment();
        attachment.originalName = originalName;
        attachment.storedName = storedName;
        attachment.storageKey = storageKey;
        attachment.extension = extension;
        attachment.sizeKb = sizeKb;
        return attachment;
    }

    public Long getId() { return id; }
    public String getOriginalName() { return originalName; }
    public String getStoredName() { return storedName; }
    public String getStorageKey() { return storageKey; }
    public String getExtension() { return extension; }
    public Long getSizeKb() { return sizeKb; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
