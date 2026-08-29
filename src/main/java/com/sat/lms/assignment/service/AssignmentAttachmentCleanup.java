package com.sat.lms.assignment.service;

import com.sat.lms.attachment.entity.AssignmentAttachment;
import com.sat.lms.attachment.entity.Attachment;
import com.sat.lms.attachment.repository.AssignmentAttachmentRepository;
import com.sat.lms.attachment.repository.AttachmentRepository;
import com.sat.lms.attachment.repository.NoticeAttachmentRepository;
import com.sat.lms.attachment.service.AttachmentStorageLifecycle;
import com.sat.lms.global.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AssignmentAttachmentCleanup {
    private static final String NOT_FOUND_MESSAGE = "존재하지 않는 과제 첨부파일입니다.";

    private final AssignmentAttachmentRepository assignmentAttachmentRepository;
    private final NoticeAttachmentRepository noticeAttachmentRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentStorageLifecycle storageLifecycle;

    public AssignmentAttachmentCleanup(AssignmentAttachmentRepository assignmentAttachmentRepository,
                                       NoticeAttachmentRepository noticeAttachmentRepository,
                                       AttachmentRepository attachmentRepository,
                                       AttachmentStorageLifecycle storageLifecycle) {
        this.assignmentAttachmentRepository = assignmentAttachmentRepository;
        this.noticeAttachmentRepository = noticeAttachmentRepository;
        this.attachmentRepository = attachmentRepository;
        this.storageLifecycle = storageLifecycle;
    }

    public void deleteOne(Long attachmentId) {
        AssignmentAttachment link = assignmentAttachmentRepository
                .findWithAssignmentAndAttachmentByAttachmentId(attachmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE));
        Attachment attachment = attachmentRepository.findByIdForUpdate(attachmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE));

        assignmentAttachmentRepository.delete(link);
        assignmentAttachmentRepository.flush();
        if (!hasAnotherLink(attachmentId)) {
            attachmentRepository.delete(attachment);
            attachmentRepository.flush();
            storageLifecycle.deleteAfterCommit(List.of(attachment.getStorageKey()));
        }
    }

    public void deleteAllForAssignment(Long assignmentId) {
        List<AssignmentAttachment> links = assignmentAttachmentRepository
                .findWithAttachmentByAssignmentId(assignmentId);
        if (links.isEmpty()) return;

        List<Long> attachmentIds = links.stream()
                .map(link -> link.getAttachment().getId()).sorted().toList();
        Map<Long, Attachment> locked = attachmentRepository.findAllByIdForUpdate(attachmentIds).stream()
                .collect(Collectors.toMap(Attachment::getId, Function.identity()));
        if (locked.size() != attachmentIds.size()) {
            throw new BusinessException(HttpStatus.CONFLICT, "과제 첨부파일 상태가 변경되었습니다.");
        }

        assignmentAttachmentRepository.deleteAll(links);
        assignmentAttachmentRepository.flush();
        List<Attachment> metadataToDelete = new ArrayList<>();
        List<String> storageKeysToDelete = new ArrayList<>();
        for (Long attachmentId : attachmentIds) {
            if (!hasAnotherLink(attachmentId)) {
                Attachment attachment = locked.get(attachmentId);
                metadataToDelete.add(attachment);
                storageKeysToDelete.add(attachment.getStorageKey());
            }
        }
        if (!metadataToDelete.isEmpty()) {
            attachmentRepository.deleteAll(metadataToDelete);
            attachmentRepository.flush();
            storageLifecycle.deleteAfterCommit(storageKeysToDelete);
        }
    }

    private boolean hasAnotherLink(Long attachmentId) {
        return assignmentAttachmentRepository.countByAttachmentId(attachmentId) > 0
                || noticeAttachmentRepository.countByAttachmentId(attachmentId) > 0
                || attachmentRepository.existsSubmissionLink(attachmentId);
    }
}
