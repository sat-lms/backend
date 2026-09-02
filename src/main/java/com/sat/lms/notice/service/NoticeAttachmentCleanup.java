package com.sat.lms.notice.service;

import com.sat.lms.attachment.entity.Attachment;
import com.sat.lms.attachment.entity.NoticeAttachment;
import com.sat.lms.attachment.repository.AttachmentRepository;
import com.sat.lms.attachment.repository.NoticeAttachmentRepository;
import com.sat.lms.attachment.service.AttachmentStorageLifecycle;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.storage.StoredFile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NoticeAttachmentCleanup {
    private static final String NOT_FOUND_ATTACHMENT_MESSAGE = "존재하지 않는 공지 첨부파일입니다.";

    private final NoticeAttachmentRepository noticeAttachmentRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentStorageLifecycle storageLifecycle;

    public NoticeAttachmentCleanup(NoticeAttachmentRepository noticeAttachmentRepository,
                                   AttachmentRepository attachmentRepository,
                                   AttachmentStorageLifecycle storageLifecycle) {
        this.noticeAttachmentRepository = noticeAttachmentRepository;
        this.attachmentRepository = attachmentRepository;
        this.storageLifecycle = storageLifecycle;
    }

    public List<String> deleteOne(Long attachmentId) {
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
            return List.of(attachment.getStorageKey());
        }
        return List.of();
    }

    public List<String> deleteAllForNotice(Long noticeId) {
        List<NoticeAttachment> links = noticeAttachmentRepository.findWithAttachmentByNoticeId(noticeId);
        if (links.isEmpty()) return List.of();

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
        }
        return List.copyOf(storageKeysToDelete);
    }

    public void compensate(List<StoredFile> uploaded) {
        storageLifecycle.compensate(uploaded);
    }

    private boolean hasAnotherLink(Long attachmentId) {
        return noticeAttachmentRepository.countByAttachmentId(attachmentId) > 0
                || attachmentRepository.existsAssignmentLink(attachmentId)
                || attachmentRepository.existsSubmissionLink(attachmentId);
    }

}
