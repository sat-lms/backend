package com.sat.lms.notice.service;

import com.sat.lms.attachment.entity.Attachment;
import com.sat.lms.attachment.entity.NoticeAttachment;
import com.sat.lms.attachment.repository.AttachmentRepository;
import com.sat.lms.attachment.repository.NoticeAttachmentRepository;
import com.sat.lms.attachment.service.AttachmentFileValidator;
import com.sat.lms.attachment.service.AttachmentStorageLifecycle;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.storage.DownloadUrl;
import com.sat.lms.global.storage.FileStorage;
import com.sat.lms.global.storage.StoredFile;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.service.MemberGuard;
import com.sat.lms.notice.entity.Notice;
import com.sat.lms.notice.repository.NoticeRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;

class NoticeAttachmentServiceTest {
    NoticeRepository noticeRepository;
    NoticeAttachmentRepository noticeAttachmentRepository;
    AttachmentRepository attachmentRepository;
    MemberGuard memberGuard;
    FileStorage fileStorage;
    NoticeAttachmentCleanup cleanup;
    AttachmentStorageLifecycle storageLifecycle;
    NoticeAttachmentService service;

    @BeforeEach
    void setUp() {
        noticeRepository = mock(NoticeRepository.class);
        noticeAttachmentRepository = mock(NoticeAttachmentRepository.class);
        attachmentRepository = mock(AttachmentRepository.class);
        memberGuard = mock(MemberGuard.class);
        fileStorage = mock(FileStorage.class);
        storageLifecycle = new AttachmentStorageLifecycle(fileStorage);
        cleanup = spy(new NoticeAttachmentCleanup(noticeAttachmentRepository, attachmentRepository, storageLifecycle));
        service = new NoticeAttachmentService(noticeRepository, noticeAttachmentRepository,
                attachmentRepository, memberGuard, fileStorage, cleanup,
                new AttachmentFileValidator(), storageLifecycle);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void adminUploadsOneFile() {
        prepareAdminAndNotice();
        MultipartFile file = file("안내.PDF", 1025);
        when(fileStorage.upload(file, "notices/10")).thenReturn(stored("안내.PDF", "a.pdf", 2L));
        when(attachmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var responses = service.upload(10L, List.of(file), 7L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getOriginalName()).isEqualTo("안내.PDF");
        assertThat(responses.get(0).getExtension()).isEqualTo("pdf");
        assertThat(responses.get(0).getSizeKb()).isEqualTo(2L);
        assertThat(responses.get(0).getFormattedSize()).isEqualTo("2 KB");
        verify(noticeAttachmentRepository).save(any(NoticeAttachment.class));
        verify(attachmentRepository).flush();
        verify(noticeAttachmentRepository).flush();
    }

    @Test
    void adminUploadsThreeFiles() {
        prepareAdminAndNotice();
        List<MultipartFile> files = List.of(file("a.pdf", 1), file("b.HWPX", 2), file("c.zip", 3));
        when(fileStorage.upload(any(), anyString())).thenReturn(
                stored("a.pdf", "a.pdf", 1L), stored("b.HWPX", "b.hwpx", 1L),
                stored("c.zip", "c.zip", 1L));
        when(attachmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.upload(10L, files, 7L)).hasSize(3);

        verify(fileStorage, times(3)).upload(any(), anyString());
        verify(attachmentRepository, times(3)).save(any());
        verify(noticeAttachmentRepository, times(3)).save(any());
        verify(noticeRepository).findByIdForUpdate(10L);
    }

    @Test
    void cumulativeLimitAllowsOnePlusTwoAndTwoPlusOne() {
        prepareAdminAndNotice();
        when(attachmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(noticeAttachmentRepository.countByNoticeId(10L)).thenReturn(1L);
        when(fileStorage.upload(any(), anyString())).thenReturn(
                stored("a.pdf", "a.pdf", 1L), stored("b.pdf", "b.pdf", 1L));

        assertThat(service.upload(10L, List.of(file("a.pdf", 1), file("b.pdf", 1)), 7L)).hasSize(2);

        clearInvocations(fileStorage, attachmentRepository, noticeAttachmentRepository);
        when(noticeAttachmentRepository.countByNoticeId(10L)).thenReturn(2L);
        when(fileStorage.upload(any(), anyString())).thenReturn(stored("c.pdf", "c.pdf", 1L));
        assertThat(service.upload(10L, List.of(file("c.pdf", 1)), 7L)).hasSize(1);
    }

    @Test
    void cumulativeLimitRejectsTwoPlusTwoAndThreePlusOneBeforeStorageOrDatabase() {
        prepareAdminAndNotice();
        when(noticeAttachmentRepository.countByNoticeId(10L)).thenReturn(2L);

        assertThatThrownBy(() -> service.upload(10L,
                List.of(file("a.pdf", 1), file("b.pdf", 1)), 7L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo("공지 첨부파일은 최대 3개까지 등록할 수 있습니다.");
                });

        when(noticeAttachmentRepository.countByNoticeId(10L)).thenReturn(3L);
        assertBadRequest(() -> service.upload(10L, List.of(file("c.pdf", 1)), 7L));
        verify(fileStorage, never()).upload(any(), anyString());
        verify(attachmentRepository, never()).save(any());
        verify(noticeAttachmentRepository, never()).save(any());
    }

    @Test
    void exactTwentyMbFileAndExactFiftyMbTotalAreAccepted() {
        prepareAdminAndNotice();
        List<MultipartFile> files = List.of(
                sizedFile("a.pdf", 20L * 1024 * 1024),
                sizedFile("b.pdf", 20L * 1024 * 1024),
                sizedFile("c.pdf", 10L * 1024 * 1024));
        when(fileStorage.upload(any(), anyString())).thenReturn(
                stored("a.pdf", "a.pdf", 20L * 1024),
                stored("b.pdf", "b.pdf", 20L * 1024),
                stored("c.pdf", "c.pdf", 10L * 1024));
        when(attachmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.upload(10L, files, 7L)).hasSize(3);

        verify(fileStorage, times(3)).upload(any(), eq("notices/10"));
    }

    @Test
    void studentCannotUpload() {
        stubMember(8L, MemberRole.STUDENT);

        assertStatus(() -> service.upload(10L, List.of(file("a.pdf", 1)), 8L), HttpStatus.FORBIDDEN);
        verify(fileStorage, never()).upload(any(), anyString());
    }

    @Test
    void missingNoticeReturnsNotFound() {
        stubMember(7L, MemberRole.ADMIN);
        when(noticeRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertStatus(() -> service.upload(404L, List.of(file("a.pdf", 1)), 7L), HttpStatus.NOT_FOUND);
        verify(fileStorage, never()).upload(any(), anyString());
    }

    @Test
    void invalidListsAndFilesAreRejectedBeforeStorage() {
        prepareAdminAndNotice();
        assertBadRequest(() -> service.upload(10L, null, 7L));
        assertBadRequest(() -> service.upload(10L, List.of(), 7L));
        assertBadRequest(() -> service.upload(10L, List.of(file("a.pdf", 1), file("b.pdf", 1),
                file("c.pdf", 1), file("d.pdf", 1)), 7L));
        assertBadRequest(() -> service.upload(10L, List.of(file("empty.pdf", 0)), 7L));
        assertBadRequest(() -> service.upload(10L, List.of(sizedFile("big.pdf", 20L * 1024 * 1024 + 1)), 7L));
        assertBadRequest(() -> service.upload(10L, List.of(sizedFile("a.pdf", 18L * 1024 * 1024),
                sizedFile("b.pdf", 18L * 1024 * 1024), sizedFile("c.pdf", 18L * 1024 * 1024)), 7L));
        for (String name : new String[]{"no-extension", ".pdf", "bad.exe", "../safe.pdf", "folder/safe.pdf", "file."}) {
            assertBadRequest(() -> service.upload(10L, List.of(file(name, 1)), 7L));
        }
        verify(fileStorage, never()).upload(any(), anyString());
    }

    @Test
    void partialUploadFailureCompensatesPreviousObjects() {
        prepareAdminAndNotice();
        MultipartFile first = file("a.pdf", 1);
        MultipartFile second = file("b.pdf", 1);
        when(fileStorage.upload(first, "notices/10")).thenReturn(stored("a.pdf", "a.pdf", 1L));
        RuntimeException original = new RuntimeException("upload failed");
        when(fileStorage.upload(second, "notices/10")).thenThrow(original);

        assertThatThrownBy(() -> service.upload(10L, List.of(first, second), 7L)).isSameAs(original);
        verify(fileStorage).delete("notices/10/a.pdf");
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void attachmentOrLinkSaveFailureCompensatesAllUploads() {
        prepareAdminAndNotice();
        MultipartFile file = file("a.pdf", 1);
        when(fileStorage.upload(file, "notices/10")).thenReturn(stored("a.pdf", "a.pdf", 1L));
        when(attachmentRepository.save(any())).thenThrow(new RuntimeException("attachment db failed"));
        assertThatThrownBy(() -> service.upload(10L, List.of(file), 7L)).hasMessage("attachment db failed");
        verify(fileStorage).delete("notices/10/a.pdf");

        reset(attachmentRepository, noticeAttachmentRepository);
        cleanup = spy(new NoticeAttachmentCleanup(noticeAttachmentRepository, attachmentRepository, storageLifecycle));
        service = new NoticeAttachmentService(noticeRepository, noticeAttachmentRepository,
                attachmentRepository, memberGuard, fileStorage, cleanup,
                new AttachmentFileValidator(), storageLifecycle);
        when(attachmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(noticeAttachmentRepository.save(any())).thenThrow(new RuntimeException("link db failed"));
        assertThatThrownBy(() -> service.upload(10L, List.of(file), 7L)).hasMessage("link db failed");
        verify(fileStorage, times(2)).delete("notices/10/a.pdf");
    }

    @Test
    void compensationFailureDoesNotReplaceOriginalFailure() {
        prepareAdminAndNotice();
        MultipartFile first = file("a.pdf", 1);
        MultipartFile second = file("b.pdf", 1);
        RuntimeException original = new RuntimeException("original upload failure");
        when(fileStorage.upload(first, "notices/10")).thenReturn(stored("a.pdf", "a.pdf", 1L));
        when(fileStorage.upload(second, "notices/10")).thenThrow(original);
        doThrow(new RuntimeException("delete failure")).when(fileStorage).delete("notices/10/a.pdf");

        assertThatThrownBy(() -> service.upload(10L, List.of(first, second), 7L)).isSameAs(original);
        verify(fileStorage, times(3)).delete("notices/10/a.pdf");
    }

    @Test
    void transactionRollbackCompensatesUploadedObject() {
        prepareAdminAndNotice();
        MultipartFile file = file("a.pdf", 1);
        when(fileStorage.upload(file, "notices/10")).thenReturn(stored("a.pdf", "a.pdf", 1L));
        when(attachmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();

        service.upload(10L, List.of(file), 7L);
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(fileStorage).delete("notices/10/a.pdf");
    }

    @Test
    void memberDownloadsNoticeAttachmentWithConfiguredExpiry() {
        stubMember(8L, MemberRole.STUDENT);
        Attachment attachment = attachment("원본.pdf", "notices/10/a.pdf");
        NoticeAttachment link = NoticeAttachment.create(mock(Notice.class), attachment);
        when(noticeAttachmentRepository.findWithNoticeAndAttachmentByAttachmentId(1L))
                .thenReturn(Optional.of(link));
        when(fileStorage.createDownloadUrl("notices/10/a.pdf"))
                .thenReturn(new DownloadUrl("https://example.test/signed", 347L));

        var response = service.getDownloadUrl(1L, 8L);

        assertThat(response.getOriginalName()).isEqualTo("원본.pdf");
        assertThat(response.getDownloadUrl()).isEqualTo("https://example.test/signed");
        assertThat(response.getExpiresIn()).isEqualTo(347L);
    }

    @Test
    void nonNoticeAttachmentCannotBeDownloaded() {
        stubMember(8L, MemberRole.STUDENT);
        when(noticeAttachmentRepository.findWithNoticeAndAttachmentByAttachmentId(99L)).thenReturn(Optional.empty());

        assertStatus(() -> service.getDownloadUrl(99L, 8L), HttpStatus.NOT_FOUND);
        verify(fileStorage, never()).createDownloadUrl(anyString());
    }

    @Test
    void adminDeleteRunsStorageDeleteOnlyAfterCommit() {
        prepareDelete(false);
        TransactionSynchronizationManager.initSynchronization();

        service.delete(1L, 7L);
        verify(fileStorage, never()).delete(anyString());
        commit();

        verify(fileStorage).delete("notices/10/a.pdf");
        verify(noticeAttachmentRepository).delete(any());
        verify(attachmentRepository).delete(any());
    }

    @Test
    void studentCannotDeleteAndDbFailureNeverDeletesStorage() {
        stubMember(8L, MemberRole.STUDENT);
        assertStatus(() -> service.delete(1L, 8L), HttpStatus.FORBIDDEN);

        prepareDelete(false);
        doThrow(new RuntimeException("db failed")).when(noticeAttachmentRepository).flush();
        assertThatThrownBy(() -> service.delete(1L, 7L)).hasMessage("db failed");
        verify(fileStorage, never()).delete(anyString());
    }

    @Test
    void rollbackKeepsStorageAndSharedAttachmentKeepsMetadataAndStorage() {
        prepareDelete(false);
        TransactionSynchronizationManager.initSynchronization();
        service.delete(1L, 7L);
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(fileStorage, never()).delete(anyString());

        clearSynchronization();
        clearInvocations(attachmentRepository, noticeAttachmentRepository, fileStorage);
        prepareDelete(true);
        service.delete(1L, 7L);
        verify(attachmentRepository, never()).delete(any());
        verify(fileStorage, never()).delete(anyString());
    }

    @Test
    void attachmentSharedWithAnotherDomainKeepsMetadataAndStorage() {
        prepareDelete(false);
        when(attachmentRepository.existsAssignmentLink(1L)).thenReturn(true);

        service.delete(1L, 7L);

        verify(noticeAttachmentRepository).delete(any());
        verify(attachmentRepository, never()).delete(any());
        verify(fileStorage, never()).delete(anyString());
    }

    @Test
    void guardFailureStopsUploadBeforeValidationDomainLookupAndSideEffects() {
        BusinessException forbidden = new BusinessException(HttpStatus.FORBIDDEN, "승인되지 않은 계정입니다.");
        when(memberGuard.requireAdmin(7L)).thenThrow(forbidden);

        assertThatThrownBy(() -> service.upload(10L, null, 7L)).isSameAs(forbidden);

        verify(memberGuard).requireAdmin(7L);
        verifyNoInteractions(noticeRepository, noticeAttachmentRepository, attachmentRepository, fileStorage);
        verify(cleanup, never()).deleteOne(any());
    }

    @Test
    void guardFailureStopsDownloadBeforeAttachmentLookupAndUrlCreation() {
        BusinessException forbidden = new BusinessException(HttpStatus.FORBIDDEN, "승인되지 않은 계정입니다.");
        when(memberGuard.requireMember(8L)).thenThrow(forbidden);

        assertThatThrownBy(() -> service.getDownloadUrl(1L, 8L)).isSameAs(forbidden);

        verify(memberGuard).requireMember(8L);
        verifyNoInteractions(noticeRepository, noticeAttachmentRepository, attachmentRepository, fileStorage);
    }

    @Test
    void guardFailureStopsDeleteBeforeCleanupAndStorage() {
        BusinessException forbidden = new BusinessException(HttpStatus.FORBIDDEN, "승인되지 않은 계정입니다.");
        when(memberGuard.requireAdmin(7L)).thenThrow(forbidden);

        assertThatThrownBy(() -> service.delete(1L, 7L)).isSameAs(forbidden);

        verify(memberGuard).requireAdmin(7L);
        verify(cleanup, never()).deleteOne(any());
        verifyNoInteractions(noticeRepository, noticeAttachmentRepository, attachmentRepository, fileStorage);
    }

    private void prepareAdminAndNotice() {
        stubMember(7L, MemberRole.ADMIN);
        Notice notice = mock(Notice.class);
        when(noticeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(notice));
    }

    private void prepareDelete(boolean shared) {
        stubMember(7L, MemberRole.ADMIN);
        Attachment attachment = attachment("a.pdf", "notices/10/a.pdf");
        NoticeAttachment link = NoticeAttachment.create(mock(Notice.class), attachment);
        when(noticeAttachmentRepository.findWithNoticeAndAttachmentByAttachmentId(1L))
                .thenReturn(Optional.of(link));
        when(attachmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(attachment));
        when(noticeAttachmentRepository.countByAttachmentId(1L)).thenReturn(shared ? 1L : 0L);
        when(attachmentRepository.existsAssignmentLink(1L)).thenReturn(false);
        when(attachmentRepository.existsSubmissionLink(1L)).thenReturn(false);
    }

    private Member member(MemberRole role) {
        Member member = mock(Member.class);
        when(member.getRole()).thenReturn(role);
        return member;
    }

    private void stubMember(Long memberId, MemberRole role) {
        Member member = member(role);
        when(memberGuard.requireMember(memberId)).thenReturn(member);
        if (role == MemberRole.ADMIN) {
            when(memberGuard.requireAdmin(memberId)).thenReturn(member);
        } else {
            when(memberGuard.requireAdmin(memberId))
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

    private StoredFile stored(String originalName, String storedName, long sizeKb) {
        String extension = storedName.substring(storedName.lastIndexOf('.') + 1);
        return new StoredFile(originalName, storedName, "notices/10/" + storedName, extension, sizeKb);
    }

    private Attachment attachment(String originalName, String storageKey) {
        return Attachment.create(originalName, "a.pdf", storageKey, "pdf", 1L);
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
