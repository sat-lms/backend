package com.sat.lms.notice.service;

import com.sat.lms.attachment.entity.Attachment;
import com.sat.lms.attachment.entity.NoticeAttachment;
import com.sat.lms.attachment.repository.AttachmentRepository;
import com.sat.lms.attachment.repository.NoticeAttachmentRepository;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.storage.FileStorage;
import com.sat.lms.global.storage.StoredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NoticeAttachmentCleanup {
    private static final Logger log = LoggerFactory.getLogger(NoticeAttachmentCleanup.class);
    private static final int MAX_DELETE_ATTEMPTS = 3;
    private static final long DELETE_RETRY_DELAY_MILLIS = 100;
    private static final String NOT_FOUND_ATTACHMENT_MESSAGE = "존재하지 않는 공지 첨부파일입니다.";

    private final NoticeAttachmentRepository noticeAttachmentRepository;
    private final AttachmentRepository attachmentRepository;
    private final FileStorage fileStorage;

    public NoticeAttachmentCleanup(NoticeAttachmentRepository noticeAttachmentRepository,
                                   AttachmentRepository attachmentRepository,
                                   FileStorage fileStorage) {
        this.noticeAttachmentRepository = noticeAttachmentRepository;
        this.attachmentRepository = attachmentRepository;
        this.fileStorage = fileStorage;
    }

    public void deleteOne(Long attachmentId) {
        NoticeAttachment link = noticeAttachmentRepository
                .findWithNoticeAndAttachmentByAttachmentId(attachmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_ATTACHMENT_MESSAGE));
        Attachment attachment = attachmentRepository.findByIdForUpdate(attachmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_ATTACHMENT_MESSAGE));

        noticeAttachmentRepository.delete(link);
        noticeAttachmentRepository.flush();
        if (!hasAnotherLink(attachmentId)) {
            attachmentRepository.delete(attachment);
            attachmentRepository.flush();
            deleteAfterCommit(List.of(attachment.getStorageKey()));
        }
    }

    public void deleteAllForNotice(Long noticeId) {
        List<NoticeAttachment> links = noticeAttachmentRepository.findWithAttachmentByNoticeId(noticeId);
        if (links.isEmpty()) return;

        List<Long> attachmentIds = links.stream()
                .map(link -> link.getAttachment().getId())
                .sorted()
                .toList();
        Map<Long, Attachment> lockedAttachments = attachmentRepository.findAllByIdForUpdate(attachmentIds).stream()
                .collect(Collectors.toMap(Attachment::getId, Function.identity()));
        if (lockedAttachments.size() != attachmentIds.size()) {
            throw new BusinessException(HttpStatus.CONFLICT, "공지 첨부파일 상태가 변경되었습니다.");
        }

        noticeAttachmentRepository.deleteAll(links);
        noticeAttachmentRepository.flush();

        List<Attachment> metadataToDelete = new ArrayList<>();
        List<String> storageKeysToDelete = new ArrayList<>();
        for (Long attachmentId : attachmentIds) {
            if (!hasAnotherLink(attachmentId)) {
                Attachment attachment = lockedAttachments.get(attachmentId);
                metadataToDelete.add(attachment);
                storageKeysToDelete.add(attachment.getStorageKey());
            }
        }
        if (!metadataToDelete.isEmpty()) {
            attachmentRepository.deleteAll(metadataToDelete);
            attachmentRepository.flush();
            deleteAfterCommit(storageKeysToDelete);
        }
    }

    public void compensate(List<StoredFile> uploaded) {
        for (StoredFile stored : uploaded) deleteWithRetry(stored.storageKey());
    }

    private boolean hasAnotherLink(Long attachmentId) {
        return noticeAttachmentRepository.countByAttachmentId(attachmentId) > 0
                || attachmentRepository.existsAssignmentLink(attachmentId)
                || attachmentRepository.existsSubmissionLink(attachmentId);
    }

    private void deleteAfterCommit(List<String> storageKeys) {
        List<String> keys = List.copyOf(storageKeys);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(keys);
                }
            });
        } else {
            deleteQuietly(keys);
        }
    }

    private void deleteQuietly(List<String> storageKeys) {
        for (String storageKey : storageKeys) deleteWithRetry(storageKey);
    }

    private void deleteWithRetry(String storageKey) {
        for (int attempt = 1; attempt <= MAX_DELETE_ATTEMPTS; attempt++) {
            try {
                fileStorage.delete(storageKey);
                return;
            } catch (RuntimeException exception) {
                if (attempt == MAX_DELETE_ATTEMPTS) {
                    log.error("S3 object cleanup failed after {} attempts", MAX_DELETE_ATTEMPTS);
                    return;
                }
                log.warn("Retrying S3 object cleanup (attempt {}/{})", attempt, MAX_DELETE_ATTEMPTS);
                if (!sleepBeforeRetry()) return;
            }
        }
    }

    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(DELETE_RETRY_DELAY_MILLIS);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting to retry S3 object cleanup");
            return false;
        }
    }
}
