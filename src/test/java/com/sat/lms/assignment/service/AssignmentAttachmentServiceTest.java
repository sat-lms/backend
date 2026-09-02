package com.sat.lms.assignment.service;

import com.sat.lms.assignment.entity.Assignment;
import com.sat.lms.assignment.repository.AssignmentRepository;
import com.sat.lms.attachment.entity.AssignmentAttachment;
import com.sat.lms.attachment.entity.Attachment;
import com.sat.lms.attachment.repository.AssignmentAttachmentRepository;
import com.sat.lms.attachment.repository.AttachmentRepository;
import com.sat.lms.attachment.repository.NoticeAttachmentRepository;
import com.sat.lms.attachment.service.AttachmentFileValidator;
import com.sat.lms.attachment.service.AttachmentStorageLifecycle;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.storage.DownloadUrl;
import com.sat.lms.global.storage.FileStorage;
import com.sat.lms.global.storage.StoredFile;
import com.sat.lms.global.transaction.ShortTransactionExecutor;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.service.MemberGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

class AssignmentAttachmentServiceTest {
    AssignmentRepository assignmentRepository;
    AssignmentAttachmentRepository assignmentAttachmentRepository;
    AttachmentRepository attachmentRepository;
    NoticeAttachmentRepository noticeAttachmentRepository;
    MemberGuard memberGuard;
    FileStorage fileStorage;
    AttachmentStorageLifecycle lifecycle;
    AssignmentAttachmentCleanup cleanup;
    AssignmentAttachmentService service;
    ShortTransactionExecutor transactions;

    @BeforeEach
    void setUp() {
        assignmentRepository = mock(AssignmentRepository.class);
        assignmentAttachmentRepository = mock(AssignmentAttachmentRepository.class);
        attachmentRepository = mock(AttachmentRepository.class);
        noticeAttachmentRepository = mock(NoticeAttachmentRepository.class);
        memberGuard = mock(MemberGuard.class);
        fileStorage = mock(FileStorage.class);
        lifecycle = new AttachmentStorageLifecycle(fileStorage);
        transactions = mock(ShortTransactionExecutor.class);
        when(transactions.read(any())).thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(0)).get());
        when(transactions.write(any(java.util.function.Supplier.class))).thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(0)).get());
        cleanup = spy(new AssignmentAttachmentCleanup(
                assignmentAttachmentRepository, noticeAttachmentRepository, attachmentRepository, lifecycle));
        service = new AssignmentAttachmentService(assignmentRepository, assignmentAttachmentRepository,
                attachmentRepository, memberGuard, fileStorage,
                new AttachmentFileValidator(), lifecycle, cleanup, transactions);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void cumulativeBoundariesAllowZeroPlusThreeOnePlusTwoAndTwoPlusOne() {
        prepareAdminAndAssignment();
        when(attachmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileStorage.upload(any(), anyString())).thenReturn(stored("a.pdf"), stored("b.pdf"), stored("c.pdf"));
        when(assignmentAttachmentRepository.countByAssignmentId(10L)).thenReturn(0L, 0L, 1L, 1L, 2L, 2L);

        assertThat(service.upload(10L, List.of(file("a.pdf", 1), file("b.pdf", 1), file("c.pdf", 1)), 7L))
                .hasSize(3);
        assertThat(service.upload(10L, List.of(file("a.pdf", 1), file("b.pdf", 1)), 7L)).hasSize(2);
        assertThat(service.upload(10L, List.of(file("a.pdf", 1)), 7L)).hasSize(1);
        verify(assignmentRepository, times(3)).findByIdForUpdate(10L);
    }

    @Test
    void cumulativeBoundariesRejectTwoPlusTwoThreePlusOneAndFourBeforeStorageOrDatabase() {
        prepareAdminAndAssignment();
        when(assignmentAttachmentRepository.countByAssignmentId(10L)).thenReturn(2L, 3L, 0L);

        assertBadRequest(() -> service.upload(10L, List.of(file("a.pdf", 1), file("b.pdf", 1)), 7L));
        assertBadRequest(() -> service.upload(10L, List.of(file("a.pdf", 1)), 7L));
        assertBadRequest(() -> service.upload(10L, List.of(file("a.pdf", 1), file("b.pdf", 1),
                file("c.pdf", 1), file("d.pdf", 1)), 7L));
        verify(fileStorage, never()).upload(any(), anyString());
        verify(attachmentRepository, never()).save(any());
        verify(assignmentAttachmentRepository, never()).save(any());
    }

    @Test
    void roleAndMissingAssignmentAreRejected() {
        stubMember(8L, MemberRole.STUDENT);
        assertStatus(() -> service.upload(10L, List.of(file("a.pdf", 1)), 8L), HttpStatus.FORBIDDEN);
        stubMember(7L, MemberRole.ADMIN);
        when(assignmentRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());
        assertStatus(() -> service.upload(404L, List.of(file("a.pdf", 1)), 7L), HttpStatus.NOT_FOUND);
    }

    @Test
    void commonFilePolicyRejectsInvalidInputBeforeStorage() {
        prepareAdminAndAssignment();
        assertBadRequest(() -> service.upload(10L, null, 7L));
        assertBadRequest(() -> service.upload(10L, List.of(), 7L));
        for (String name : new String[]{"empty.pdf", "no-extension", ".pdf", "bad.exe", "../safe.pdf", "folder/safe.pdf"}) {
            int size = name.equals("empty.pdf") ? 0 : 1;
            assertBadRequest(() -> service.upload(10L, List.of(file(name, size)), 7L));
        }
        assertBadRequest(() -> service.upload(10L,
                List.of(sizedFile("big.pdf", 20L * 1024 * 1024 + 1)), 7L));
        assertBadRequest(() -> service.upload(10L, List.of(
                sizedFile("a.pdf", 18L * 1024 * 1024), sizedFile("b.pdf", 18L * 1024 * 1024),
                sizedFile("c.pdf", 18L * 1024 * 1024)), 7L));
        verify(fileStorage, never()).upload(any(), anyString());
    }

    @Test
    void exactTwentyMbAndExactFiftyMbAreAccepted() {
        prepareAdminAndAssignment();
        when(attachmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileStorage.upload(any(), anyString())).thenReturn(stored("a.pdf"), stored("b.pdf"), stored("c.pdf"));
        List<MultipartFile> files = List.of(sizedFile("a.pdf", 20L * 1024 * 1024),
                sizedFile("b.pdf", 20L * 1024 * 1024), sizedFile("c.pdf", 10L * 1024 * 1024));
        assertThat(service.upload(10L, files, 7L)).hasSize(3);
    }

    @Test
    void partialUploadAndDatabaseFailuresCompensateAllObjects() {
        prepareAdminAndAssignment();
        MultipartFile first = file("a.pdf", 1);
        MultipartFile second = file("b.pdf", 1);
        when(fileStorage.upload(first, "assignments/10")).thenReturn(stored("a.pdf"));
        RuntimeException original = new RuntimeException("upload failed");
        when(fileStorage.upload(second, "assignments/10")).thenThrow(original);
        assertThatThrownBy(() -> service.upload(10L, List.of(first, second), 7L)).isSameAs(original);
        verify(fileStorage).delete("assignments/10/a.pdf");

        when(fileStorage.upload(first, "assignments/10")).thenReturn(stored("a.pdf"));
        when(attachmentRepository.save(any())).thenThrow(new RuntimeException("db failed"));
        assertThatThrownBy(() -> service.upload(10L, List.of(first), 7L)).hasMessage("db failed");
        verify(fileStorage, times(2)).delete("assignments/10/a.pdf");
    }

    @Test
    void rollbackCompensatesAndCompensationFailureDoesNotReplaceOriginal() {
        prepareAdminAndAssignment();
        MultipartFile file = file("a.pdf", 1);
        when(fileStorage.upload(file, "assignments/10")).thenReturn(stored("a.pdf"));
        when(attachmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactions.write(any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
            ((java.util.function.Supplier<?>) invocation.getArgument(0)).get();
            throw new RuntimeException("rolled back");
        });
        assertThatThrownBy(() -> service.upload(10L, List.of(file), 7L)).hasMessage("rolled back");
        verify(fileStorage).delete("assignments/10/a.pdf");

        doThrow(new RuntimeException("cleanup failed")).when(fileStorage).delete(anyString());
        when(attachmentRepository.save(any())).thenThrow(new RuntimeException("original db failure"));
        assertThatThrownBy(() -> service.upload(10L, List.of(file), 7L)).hasMessage("original db failure");
    }

    @Test
    void assignmentLinkFlushFailureCompensatesUploadedObject() {
        prepareAdminAndAssignment();
        MultipartFile file = file("a.pdf", 1);
        when(fileStorage.upload(file, "assignments/10")).thenReturn(stored("a.pdf"));
        when(attachmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("link flush failed")).when(assignmentAttachmentRepository).flush();

        assertThatThrownBy(() -> service.upload(10L, List.of(file), 7L)).hasMessage("link flush failed");

        verify(fileStorage).delete("assignments/10/a.pdf");
    }

    @Test
    void authenticatedMembersDownloadOnlyActualAssignmentAttachment() {
        stubMember(8L, MemberRole.STUDENT);
        Attachment attachment = mock(Attachment.class);
        when(attachment.getStorageKey()).thenReturn("assignments/10/a.pdf");
        when(attachment.getOriginalName()).thenReturn("원본.pdf");
        AssignmentAttachment link = mock(AssignmentAttachment.class);
        when(link.getAttachment()).thenReturn(attachment);
        when(assignmentAttachmentRepository.findWithAssignmentAndAttachmentByAttachmentId(1L))
                .thenReturn(Optional.of(link));
        when(fileStorage.createDownloadUrl("assignments/10/a.pdf"))
                .thenReturn(new DownloadUrl("https://signed.test", 347L));

        var response = service.getDownloadUrl(1L, 8L);
        assertThat(response.getDownloadUrl()).isEqualTo("https://signed.test");
        assertThat(response.getExpiresIn()).isEqualTo(347L);
        assertThat(response.getOriginalName()).isEqualTo("원본.pdf");
        stubMember(7L, MemberRole.ADMIN);
        assertThat(service.getDownloadUrl(1L, 7L).getDownloadUrl()).isEqualTo("https://signed.test");

        clearInvocations(fileStorage);
        when(assignmentAttachmentRepository.findWithAssignmentAndAttachmentByAttachmentId(99L))
                .thenReturn(Optional.empty());
        assertStatus(() -> service.getDownloadUrl(99L, 8L), HttpStatus.NOT_FOUND);
        verify(fileStorage, never()).createDownloadUrl(anyString());
    }

    @Test
    void deleteRequiresAdminAndDeletesStorageOnlyAfterCommit() {
        stubMember(8L, MemberRole.STUDENT);
        assertStatus(() -> service.delete(1L, 8L), HttpStatus.FORBIDDEN);
        prepareDelete(false, false, false);
        service.delete(1L, 7L);
        verify(fileStorage).delete("assignments/10/a.pdf");
        verify(attachmentRepository).delete(any());
    }

    @Test
    void noticeLinkProtectsMetadataAndStorage() {
        prepareDelete(true, false, false);
        service.delete(1L, 7L);
        verify(attachmentRepository, never()).delete(any());
        verify(fileStorage, never()).delete(anyString());
    }

    @Test
    void anotherAssignmentLinkProtectsMetadataAndStorage() {
        prepareDelete(false, true, false);
        service.delete(1L, 7L);
        verify(attachmentRepository, never()).delete(any());
        verify(fileStorage, never()).delete(anyString());
    }

    @Test
    void submissionLinkProtectsMetadataAndStorage() {
        prepareDelete(false, false, true);
        service.delete(1L, 7L);
        verify(attachmentRepository, never()).delete(any());
        verify(fileStorage, never()).delete(anyString());
    }

    @Test
    void nonexistentAssignmentAttachmentCannotBeDeleted() {
        stubMember(7L, MemberRole.ADMIN);
        when(assignmentAttachmentRepository.findWithAssignmentAndAttachmentByAttachmentId(99L))
                .thenReturn(Optional.empty());
        assertStatus(() -> service.delete(99L, 7L), HttpStatus.NOT_FOUND);
        verify(fileStorage, never()).delete(anyString());
    }

    @Test
    void deleteDatabaseFailureAndRollbackKeepStorage() {
        prepareDelete(false, false, false);
        doThrow(new RuntimeException("db failed")).when(assignmentAttachmentRepository).flush();
        assertThatThrownBy(() -> service.delete(1L, 7L)).hasMessage("db failed");
        verify(fileStorage, never()).delete(anyString());
    }

    @Test
    void guardFailureStopsUploadBeforeValidationDomainLookupAndSideEffects() {
        BusinessException forbidden = new BusinessException(HttpStatus.FORBIDDEN, "승인되지 않은 계정입니다.");
        when(memberGuard.requireAdmin(7L)).thenThrow(forbidden);

        assertThatThrownBy(() -> service.upload(10L, null, 7L)).isSameAs(forbidden);

        verify(memberGuard).requireAdmin(7L);
        verifyNoInteractions(assignmentRepository, assignmentAttachmentRepository, attachmentRepository,
                noticeAttachmentRepository, fileStorage);
        verify(cleanup, never()).deleteOne(any());
    }

    @Test
    void guardFailureStopsDownloadBeforeAttachmentLookupAndUrlCreation() {
        BusinessException forbidden = new BusinessException(HttpStatus.FORBIDDEN, "승인되지 않은 계정입니다.");
        when(memberGuard.requireMember(8L)).thenThrow(forbidden);

        assertThatThrownBy(() -> service.getDownloadUrl(1L, 8L)).isSameAs(forbidden);

        verify(memberGuard).requireMember(8L);
        verifyNoInteractions(assignmentRepository, assignmentAttachmentRepository, attachmentRepository,
                noticeAttachmentRepository, fileStorage);
    }

    @Test
    void guardFailureStopsDeleteBeforeCleanupAndStorage() {
        BusinessException forbidden = new BusinessException(HttpStatus.FORBIDDEN, "승인되지 않은 계정입니다.");
        when(memberGuard.requireAdmin(7L)).thenThrow(forbidden);

        assertThatThrownBy(() -> service.delete(1L, 7L)).isSameAs(forbidden);

        verify(memberGuard).requireAdmin(7L);
        verify(cleanup, never()).deleteOne(any());
        verifyNoInteractions(assignmentRepository, assignmentAttachmentRepository, attachmentRepository,
                noticeAttachmentRepository, fileStorage);
    }

    private void prepareAdminAndAssignment() {
        stubMember(7L, MemberRole.ADMIN);
        Assignment assignment = mock(Assignment.class);
        when(assignmentRepository.findById(10L)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(assignment));
    }

    private void prepareDelete(boolean noticeShared, boolean assignmentShared, boolean submissionShared) {
        stubMember(7L, MemberRole.ADMIN);
        Attachment attachment = mock(Attachment.class);
        when(attachment.getStorageKey()).thenReturn("assignments/10/a.pdf");
        AssignmentAttachment link = mock(AssignmentAttachment.class);
        when(link.getAttachment()).thenReturn(attachment);
        when(assignmentAttachmentRepository.findWithAssignmentAndAttachmentByAttachmentId(1L))
                .thenReturn(Optional.of(link));
        when(attachmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(attachment));
        when(assignmentAttachmentRepository.countByAttachmentId(1L)).thenReturn(assignmentShared ? 1L : 0L);
        when(noticeAttachmentRepository.countByAttachmentId(1L)).thenReturn(noticeShared ? 1L : 0L);
        when(attachmentRepository.existsSubmissionLink(1L)).thenReturn(submissionShared);
    }

    private void stubMember(Long id, MemberRole role) {
        Member member = mock(Member.class);
        when(member.getRole()).thenReturn(role);
        when(memberGuard.requireMember(id)).thenReturn(member);
        if (role == MemberRole.ADMIN) {
            when(memberGuard.requireAdmin(id)).thenReturn(member);
        } else {
            when(memberGuard.requireAdmin(id))
                    .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."));
        }
    }

    private MultipartFile file(String name, int size) {
        return new MockMultipartFile("files", name, "application/octet-stream", new byte[size]);
    }

    private MultipartFile sizedFile(String name, long size) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(name);
        when(file.getSize()).thenReturn(size);
        when(file.isEmpty()).thenReturn(false);
        return file;
    }

    private StoredFile stored(String name) {
        return new StoredFile(name, name, "assignments/10/" + name, "pdf", 1L);
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

    private void assertBadRequest(Runnable action) { assertStatus(action, HttpStatus.BAD_REQUEST); }

    private void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getStatus()).isEqualTo(status));
    }
}
