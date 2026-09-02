package com.sat.lms.notice.service;

import com.sat.lms.attachment.entity.Attachment;
import com.sat.lms.attachment.entity.NoticeAttachment;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoticeAttachmentCleanupTest {
    NoticeAttachmentRepository noticeAttachmentRepository;
    AttachmentRepository attachmentRepository;
    FileStorage fileStorage;
    NoticeAttachmentCleanup cleanup;

    @BeforeEach
    void setUp() {
        noticeAttachmentRepository = mock(NoticeAttachmentRepository.class);
        attachmentRepository = mock(AttachmentRepository.class);
        fileStorage = mock(FileStorage.class);
        cleanup = new NoticeAttachmentCleanup(noticeAttachmentRepository, attachmentRepository,
                new AttachmentStorageLifecycle(fileStorage));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void unsharedSingleAttachmentDeletesMetadataAndStorageOnlyAfterCommit() {
        prepareSingle(false, false, false);
        List<String> keys = cleanup.deleteOne(1L);

        verify(attachmentRepository).delete(any(Attachment.class));
        assertThat(keys).containsExactly("notices/10/a.pdf");
        verify(fileStorage, never()).delete(any());
    }

    @Test
    void attachmentSharedWithAnotherNoticeKeepsMetadataAndStorage() {
        prepareSingle(true, false, false);
        cleanup.deleteOne(1L);
        verify(noticeAttachmentRepository).delete(any());
        verify(attachmentRepository, never()).delete(any());
        verify(fileStorage, never()).delete(any());
    }

    @Test
    void attachmentSharedWithAssignmentKeepsMetadataAndStorage() {
        prepareSingle(false, true, false);
        cleanup.deleteOne(1L);
        verify(attachmentRepository, never()).delete(any());
        verify(fileStorage, never()).delete(any());
    }

    @Test
    void attachmentSharedWithSubmissionKeepsMetadataAndStorage() {
        prepareSingle(false, false, true);
        cleanup.deleteOne(1L);
        verify(attachmentRepository, never()).delete(any());
        verify(fileStorage, never()).delete(any());
    }

    @Test
    void rollbackNeverDeletesStorage() {
        prepareSingle(false, false, false);
        TransactionSynchronizationManager.initSynchronization();
        cleanup.deleteOne(1L);
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(fileStorage, never()).delete(any());
    }

    @Test
    void noticeCleanupDeletesAllUnsharedAttachmentsAfterCommit() {
        Attachment first = attachment(1L, "notices/10/a.pdf");
        Attachment second = attachment(2L, "notices/10/b.pdf");
        NoticeAttachment firstLink = link(first);
        NoticeAttachment secondLink = link(second);
        when(noticeAttachmentRepository.findWithAttachmentByNoticeId(10L))
                .thenReturn(List.of(firstLink, secondLink));
        when(attachmentRepository.findAllByIdForUpdate(List.of(1L, 2L))).thenReturn(List.of(first, second));
        List<String> keys = cleanup.deleteAllForNotice(10L);

        verify(attachmentRepository).deleteAll(List.of(first, second));
        verify(fileStorage, never()).delete(any());
        assertThat(keys).containsExactly("notices/10/a.pdf", "notices/10/b.pdf");
        verify(fileStorage, never()).delete(any());
    }

    @Test
    void noticeCleanupDeletesOnlyUnsharedAttachment() {
        Attachment unshared = attachment(1L, "notices/10/a.pdf");
        Attachment shared = attachment(2L, "notices/10/b.pdf");
        NoticeAttachment unsharedLink = link(unshared);
        NoticeAttachment sharedLink = link(shared);
        when(noticeAttachmentRepository.findWithAttachmentByNoticeId(10L))
                .thenReturn(List.of(unsharedLink, sharedLink));
        when(attachmentRepository.findAllByIdForUpdate(List.of(1L, 2L))).thenReturn(List.of(unshared, shared));
        when(attachmentRepository.existsAssignmentLink(2L)).thenReturn(true);
        List<String> keys = cleanup.deleteAllForNotice(10L);

        verify(attachmentRepository).deleteAll(List.of(unshared));
        assertThat(keys).containsExactly("notices/10/a.pdf");
        verify(fileStorage, never()).delete("notices/10/a.pdf");
        verify(fileStorage, never()).delete("notices/10/b.pdf");
    }

    @Test
    void databaseFailureRegistersNoStorageDeletion() {
        Attachment attachment = attachment(1L, "notices/10/a.pdf");
        NoticeAttachment link = link(attachment);
        when(noticeAttachmentRepository.findWithAttachmentByNoticeId(10L)).thenReturn(List.of(link));
        when(attachmentRepository.findAllByIdForUpdate(List.of(1L))).thenReturn(List.of(attachment));
        doThrow(new RuntimeException("db failed")).when(attachmentRepository).flush();
        TransactionSynchronizationManager.initSynchronization();

        assertThatThrownBy(() -> cleanup.deleteAllForNotice(10L)).hasMessage("db failed");
        verify(fileStorage, never()).delete(any());
    }

    @Test
    void afterCommitStorageFailureIsRetriedAndDoesNotEscape() {
        prepareSingle(false, false, false);
        List<String> keys = cleanup.deleteOne(1L);
        assertThat(keys).containsExactly("notices/10/a.pdf");
        verify(fileStorage, never()).delete(any());
    }

    private void prepareSingle(boolean noticeShared, boolean assignmentShared, boolean submissionShared) {
        Attachment attachment = attachment(1L, "notices/10/a.pdf");
        NoticeAttachment link = link(attachment);
        when(noticeAttachmentRepository.findWithNoticeAndAttachmentByAttachmentId(1L))
                .thenReturn(Optional.of(link));
        when(attachmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(attachment));
        when(noticeAttachmentRepository.countByAttachmentId(1L)).thenReturn(noticeShared ? 1L : 0L);
        when(attachmentRepository.existsAssignmentLink(1L)).thenReturn(assignmentShared);
        when(attachmentRepository.existsSubmissionLink(1L)).thenReturn(submissionShared);
    }

    private Attachment attachment(Long id, String key) {
        Attachment attachment = mock(Attachment.class);
        when(attachment.getId()).thenReturn(id);
        when(attachment.getStorageKey()).thenReturn(key);
        return attachment;
    }

    private NoticeAttachment link(Attachment attachment) {
        NoticeAttachment link = mock(NoticeAttachment.class);
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

    private void complete(int status) {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(status);
        }
        TransactionSynchronizationManager.clearSynchronization();
    }
}
