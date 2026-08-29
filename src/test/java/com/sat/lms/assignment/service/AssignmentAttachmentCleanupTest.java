package com.sat.lms.assignment.service;

import com.sat.lms.attachment.entity.AssignmentAttachment;
import com.sat.lms.attachment.entity.Attachment;
import com.sat.lms.attachment.repository.AssignmentAttachmentRepository;
import com.sat.lms.attachment.repository.AttachmentRepository;
import com.sat.lms.attachment.repository.NoticeAttachmentRepository;
import com.sat.lms.attachment.service.AttachmentStorageLifecycle;
import com.sat.lms.global.storage.FileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssignmentAttachmentCleanupTest {
    AssignmentAttachmentRepository assignmentAttachmentRepository;
    NoticeAttachmentRepository noticeAttachmentRepository;
    AttachmentRepository attachmentRepository;
    FileStorage fileStorage;
    AssignmentAttachmentCleanup cleanup;

    @BeforeEach
    void setUp() {
        assignmentAttachmentRepository = mock(AssignmentAttachmentRepository.class);
        noticeAttachmentRepository = mock(NoticeAttachmentRepository.class);
        attachmentRepository = mock(AttachmentRepository.class);
        fileStorage = mock(FileStorage.class);
        cleanup = new AssignmentAttachmentCleanup(assignmentAttachmentRepository,
                noticeAttachmentRepository, attachmentRepository, new AttachmentStorageLifecycle(fileStorage));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void assignmentDeleteRemovesAllUnsharedMetadataAfterCommit() {
        Attachment first = attachment(1L, "assignments/10/a.pdf");
        Attachment second = attachment(2L, "assignments/10/b.pdf");
        prepareAll(List.of(first, second));
        TransactionSynchronizationManager.initSynchronization();

        cleanup.deleteAllForAssignment(10L);

        verify(assignmentAttachmentRepository).deleteAll(org.mockito.ArgumentMatchers.anyList());
        verify(attachmentRepository).deleteAll(List.of(first, second));
        verify(fileStorage, never()).delete(org.mockito.ArgumentMatchers.anyString());
        commit();
        verify(fileStorage).delete("assignments/10/a.pdf");
        verify(fileStorage).delete("assignments/10/b.pdf");
    }

    @Test
    void noticeSharedAttachmentIsProtectedWhileUnsharedAttachmentIsDeleted() {
        Attachment unshared = attachment(1L, "assignments/10/a.pdf");
        Attachment shared = attachment(2L, "assignments/10/b.pdf");
        prepareAll(List.of(unshared, shared));
        when(noticeAttachmentRepository.countByAttachmentId(2L)).thenReturn(1L);
        TransactionSynchronizationManager.initSynchronization();

        cleanup.deleteAllForAssignment(10L);
        commit();

        verify(attachmentRepository).deleteAll(List.of(unshared));
        verify(fileStorage).delete("assignments/10/a.pdf");
        verify(fileStorage, never()).delete("assignments/10/b.pdf");
    }

    @Test
    void anotherAssignmentOrSubmissionLinksProtectAttachment() {
        Attachment assignmentShared = attachment(1L, "assignments/10/a.pdf");
        Attachment submissionShared = attachment(2L, "assignments/10/b.pdf");
        prepareAll(List.of(assignmentShared, submissionShared));
        when(assignmentAttachmentRepository.countByAttachmentId(1L)).thenReturn(1L);
        when(attachmentRepository.existsSubmissionLink(2L)).thenReturn(true);

        cleanup.deleteAllForAssignment(10L);

        verify(attachmentRepository, never()).deleteAll(org.mockito.ArgumentMatchers.anyList());
        verify(fileStorage, never()).delete(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void databaseFailureOrRollbackNeverDeletesStorage() {
        Attachment attachment = attachment(1L, "assignments/10/a.pdf");
        prepareAll(List.of(attachment));
        doThrow(new RuntimeException("db failed")).when(assignmentAttachmentRepository).flush();
        assertThatThrownBy(() -> cleanup.deleteAllForAssignment(10L)).hasMessage("db failed");
        verify(fileStorage, never()).delete(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void transactionRollbackDoesNotDeleteExistingStorage() {
        Attachment attachment = attachment(1L, "assignments/10/a.pdf");
        prepareAll(List.of(attachment));
        TransactionSynchronizationManager.initSynchronization();

        cleanup.deleteAllForAssignment(10L);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        TransactionSynchronizationManager.clearSynchronization();

        verify(fileStorage, never()).delete(org.mockito.ArgumentMatchers.anyString());
    }

    private void prepareAll(List<Attachment> attachments) {
        List<AssignmentAttachment> links = attachments.stream().map(this::link).toList();
        when(assignmentAttachmentRepository.findWithAttachmentByAssignmentId(10L)).thenReturn(links);
        when(attachmentRepository.findAllByIdForUpdate(
                attachments.stream().map(Attachment::getId).toList())).thenReturn(attachments);
    }

    private Attachment attachment(Long id, String key) {
        Attachment attachment = mock(Attachment.class);
        when(attachment.getId()).thenReturn(id);
        when(attachment.getStorageKey()).thenReturn(key);
        return attachment;
    }

    private AssignmentAttachment link(Attachment attachment) {
        AssignmentAttachment link = mock(AssignmentAttachment.class);
        when(link.getAttachment()).thenReturn(attachment);
        return link;
    }

    private void commit() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
            synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        TransactionSynchronizationManager.clearSynchronization();
    }
}
