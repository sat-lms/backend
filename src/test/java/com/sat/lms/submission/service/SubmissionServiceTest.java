package com.sat.lms.submission.service;

import com.sat.lms.assignment.entity.Assignment;
import com.sat.lms.assignment.repository.AssignmentRepository;
import com.sat.lms.attachment.entity.Attachment;
import com.sat.lms.attachment.entity.SubmissionAttachment;
import com.sat.lms.attachment.repository.AttachmentRepository;
import com.sat.lms.attachment.repository.SubmissionAttachmentRepository;
import com.sat.lms.attachment.service.AttachmentStorageLifecycle;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.storage.DownloadUrl;
import com.sat.lms.global.storage.FileStorage;
import com.sat.lms.global.storage.StoredFile;
import com.sat.lms.global.transaction.ShortTransactionExecutor;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.service.MemberGuard;
import com.sat.lms.submission.dto.SubmissionCreateRequest;
import com.sat.lms.submission.dto.SubmissionFileResponse;
import com.sat.lms.submission.dto.SubmissionListResponse;
import com.sat.lms.submission.dto.MySubmissionSort;
import com.sat.lms.submission.entity.Submission;
import com.sat.lms.submission.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubmissionServiceTest {
    SubmissionRepository submissionRepository;
    AssignmentRepository assignmentRepository;
    MemberGuard memberGuard;
    AttachmentRepository attachmentRepository;
    SubmissionAttachmentRepository submissionAttachmentRepository;
    FileStorage fileStorage;
    AttachmentStorageLifecycle storageLifecycle;
    ShortTransactionExecutor transactions;
    SubmissionService service;

    @BeforeEach
    void setUp() {
        submissionRepository = mock(SubmissionRepository.class);
        assignmentRepository = mock(AssignmentRepository.class);
        memberGuard = mock(MemberGuard.class);
        attachmentRepository = mock(AttachmentRepository.class);
        submissionAttachmentRepository = mock(SubmissionAttachmentRepository.class);
        fileStorage = mock(FileStorage.class);
        storageLifecycle = new AttachmentStorageLifecycle(fileStorage);
        transactions = mock(ShortTransactionExecutor.class);
        when(transactions.read(any())).thenAnswer(invocation ->
                ((java.util.function.Supplier<?>) invocation.getArgument(0)).get());
        when(transactions.write(any(java.util.function.Supplier.class))).thenAnswer(invocation ->
                ((java.util.function.Supplier<?>) invocation.getArgument(0)).get());
        when(transactions.writeWithRollbackConfirmation(any())).thenAnswer(invocation -> {
            try {
                return ((java.util.function.Supplier<?>) invocation.getArgument(0)).get();
            } catch (RuntimeException exception) {
                throw new ShortTransactionExecutor.ConfirmedRollbackException(exception);
            }
        });
        when(assignmentRepository.findByIdForUpdate(any())).thenAnswer(invocation ->
                assignmentRepository.findById(invocation.getArgument(0)));
        when(submissionRepository.findByAssignmentIdAndStudentIdForUpdate(any(), any())).thenAnswer(invocation ->
                submissionRepository.findByAssignmentIdAndStudentId(
                        invocation.getArgument(0), invocation.getArgument(1)));
        service = new SubmissionService(submissionRepository, assignmentRepository, memberGuard,
                attachmentRepository, submissionAttachmentRepository, fileStorage, Clock.systemUTC(),
                storageLifecycle, transactions, new SubmissionStatusCalculator());

        when(submissionRepository.save(any())).thenAnswer(invocation -> {
            Submission submission = invocation.getArgument(0);
            if (submission == null) return null;
            setId(submission, 42L);
            return submission;
        });
    }

    @Test
    void studentCanSubmitTextOnly() {
        givenStudentAndAssignment(3L, false, OffsetDateTime.now().plusDays(1));

        var response = service.submit(1L, 3L, request("제출 내용"), null);

        assertThat(response.getTextContent()).isEqualTo("제출 내용");
        assertThat(response.getIsLate()).isFalse();
        assertThat(response.getFiles()).isEmpty();
        verify(fileStorage, never()).upload(any(), anyString());
    }

    @Test
    void studentCanSubmitFilesOnly() {
        givenStudentAndAssignment(3L, false, OffsetDateTime.now().plusDays(1));
        MultipartFile file = multipartFile("Member.java", 1024);
        when(fileStorage.upload(eq(file), startsWith("submissions/"))).thenReturn(
                new StoredFile("Member.java", "uuid.java", "submissions/42/uuid.java", "java", 1L));

        var response = service.submit(1L, 3L, request(null), List.of(file));

        assertThat(response.getFiles()).hasSize(1);
        assertThat(response.getFiles().get(0).getOriginalName()).isEqualTo("Member.java");
        verify(fileStorage).upload(eq(file), startsWith("submissions/"));
        verify(attachmentRepository).saveAll(any());
        verify(submissionAttachmentRepository).saveAll(any());
    }

    @Test
    void studentCanSubmitTextAndFiles() {
        givenStudentAndAssignment(3L, false, OffsetDateTime.now().plusDays(1));
        MultipartFile file = multipartFile("결과.png", 2048);
        when(fileStorage.upload(eq(file), startsWith("submissions/"))).thenReturn(
                new StoredFile("결과.png", "uuid.png", "submissions/42/uuid.png", "png", 2L));

        var response = service.submit(1L, 3L, request("텍스트"), List.of(file));

        assertThat(response.getTextContent()).isEqualTo("텍스트");
        assertThat(response.getFiles()).hasSize(1);
    }

    @Test
    void emptyTextAndFilesReturnsBadRequest() {
        givenStudentAndAssignment(3L, false, OffsetDateTime.now().plusDays(1));

        assertThatThrownBy(() -> service.submit(1L, 3L, request("   "), List.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void lateSubmissionIsBlockedWhenNotAllowed() {
        givenStudentAndAssignment(3L, false, OffsetDateTime.now().minusDays(1));

        assertThatThrownBy(() -> service.submit(1L, 3L, request("내용"), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void lateSubmissionIsSavedAsLateWhenAllowed() {
        givenStudentAndAssignment(3L, true, OffsetDateTime.now().minusDays(1));

        var response = service.submit(1L, 3L, request("내용"), null);

        assertThat(response.getIsLate()).isTrue();
    }

    @Test
    void submitUsesInstantBoundaryFromFixedClock() {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        useClock(Clock.fixed(now, ZoneOffset.UTC));

        givenStudentAndAssignment(3L, false, OffsetDateTime.ofInstant(now.plusSeconds(1), ZoneOffset.UTC));
        assertThat(service.submit(1L, 3L, request("before"), null).getIsLate()).isFalse();

        givenStudentAndAssignment(3L, false, OffsetDateTime.ofInstant(now, ZoneOffset.ofHours(9)));
        assertThat(service.submit(1L, 3L, request("equal"), null).getIsLate()).isFalse();

        clearInvocations(submissionRepository, attachmentRepository, fileStorage);
        givenStudentAndAssignment(3L, false, OffsetDateTime.ofInstant(now.minusSeconds(1), ZoneOffset.UTC));
        assertThatThrownBy(() -> service.submit(1L, 3L, request("after"), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(fileStorage, never()).upload(any(), anyString());
        verify(submissionRepository, never()).save(any());
        verify(attachmentRepository, never()).saveAll(any());
    }

    @Test
    void submitAfterDeadlineIsLateWhenAllowedByFixedClock() {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        useClock(Clock.fixed(now, ZoneOffset.UTC));
        givenStudentAndAssignment(3L, true, OffsetDateTime.ofInstant(now.minusSeconds(1), ZoneOffset.ofHours(-4)));

        assertThat(service.submit(1L, 3L, request("late"), null).getIsLate()).isTrue();
    }

    @Test
    void resubmitUsesSameFixedClockBoundaryPolicy() {
        Instant now = Instant.parse("2026-08-30T00:00:00Z");
        useClock(Clock.fixed(now, ZoneOffset.UTC));
        Member student = student(3L);
        Assignment assignment = assignment(true, OffsetDateTime.ofInstant(now.minusSeconds(1), ZoneOffset.ofHours(9)));
        Submission submission = existingSubmission(5L, student, assignment, "old", false);
        stubApprovedMember(3L, student);
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(submissionRepository.findByAssignmentIdAndStudentId(1L, 3L)).thenReturn(Optional.of(submission));
        when(submissionAttachmentRepository.findWithAttachmentBySubmissionId(5L)).thenReturn(List.of());

        assertThat(service.resubmit(1L, 3L, request("late"), null).getIsLate()).isTrue();

        Assignment blocked = assignment(false, OffsetDateTime.ofInstant(now.minusSeconds(1), ZoneOffset.UTC));
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(blocked));
        assertThatThrownBy(() -> service.resubmit(1L, 3L, request("blocked"), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(fileStorage, never()).upload(any(), anyString());
    }

    @Test
    void duplicateSubmissionPropagatesConflictFromRepository() {
        givenStudentAndAssignment(3L, false, OffsetDateTime.now().plusDays(1));
        when(submissionRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.submit(1L, 3L, request("내용"), null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void tooManyFilesReturnsBadRequest() {
        givenStudentAndAssignment(3L, false, OffsetDateTime.now().plusDays(1));
        List<MultipartFile> files = List.of(
                multipartFile("a.txt", 1), multipartFile("b.txt", 1), multipartFile("c.txt", 1),
                multipartFile("d.txt", 1), multipartFile("e.txt", 1), multipartFile("f.txt", 1));

        assertThatThrownBy(() -> service.submit(1L, 3L, request(null), files))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(submissionRepository, never()).save(any());
        verify(fileStorage, never()).upload(any(), anyString());
    }

    @Test
    void oversizedFileReturnsBadRequest() {
        givenStudentAndAssignment(3L, false, OffsetDateTime.now().plusDays(1));
        MultipartFile file = multipartFile("big.zip", 50L * 1024 * 1024 + 1);

        assertThatThrownBy(() -> service.submit(1L, 3L, request(null), List.of(file)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void totalSizeExceedingLimitReturnsBadRequest() {
        givenStudentAndAssignment(3L, false, OffsetDateTime.now().plusDays(1));
        long each = 21L * 1024 * 1024;
        List<MultipartFile> files = List.of(
                multipartFile("a.zip", each), multipartFile("b.zip", each),
                multipartFile("c.zip", each), multipartFile("d.zip", each), multipartFile("e.zip", each));

        assertThatThrownBy(() -> service.submit(1L, 3L, request(null), files))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void disallowedExtensionReturnsBadRequest() {
        givenStudentAndAssignment(3L, false, OffsetDateTime.now().plusDays(1));
        for (String filename : List.of("virus.exe", ".sh")) {
            MultipartFile file = multipartFile(filename, 10);
            assertThatThrownBy(() -> service.submit(1L, 3L, request(null), List.of(file)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    void emptyFileReturnsBadRequest() {
        givenStudentAndAssignment(3L, false, OffsetDateTime.now().plusDays(1));
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);
        when(file.getOriginalFilename()).thenReturn("empty.txt");

        assertThatThrownBy(() -> service.submit(1L, 3L, request(null), List.of(file)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void adminCannotSubmit() {
        Member admin = mock(Member.class);
        when(admin.getRole()).thenReturn(MemberRole.ADMIN);
        stubApprovedMember(7L, admin);

        assertThatThrownBy(() -> service.submit(1L, 7L, request("내용"), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(assignmentRepository, never()).findById(any());
    }

    @Test
    void unknownMemberReturnsNotFound() {
        stubMissingMember(99L);
        assertThatThrownBy(() -> service.submit(1L, 99L, request("내용"), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void missingAssignmentReturnsNotFound() {
        Member student = student(3L);
        stubApprovedMember(3L, student);
        when(assignmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(99L, 3L, request("내용"), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void dbFailureAfterUploadDeletesOrphanedS3Objects() {
        givenStudentAndAssignment(3L, false, OffsetDateTime.now().plusDays(1));
        MultipartFile fileA = multipartFile("a.txt", 10);
        MultipartFile fileB = multipartFile("b.txt", 10);
        StoredFile storedA = new StoredFile("a.txt", "uuidA.txt", "submissions/42/uuidA.txt", "txt", 1L);
        StoredFile storedB = new StoredFile("b.txt", "uuidB.txt", "submissions/42/uuidB.txt", "txt", 1L);
        when(fileStorage.upload(eq(fileA), startsWith("submissions/"))).thenReturn(storedA);
        when(fileStorage.upload(eq(fileB), startsWith("submissions/"))).thenReturn(storedB);
        when(attachmentRepository.saveAll(any())).thenThrow(new DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> service.submit(1L, 3L, request(null), List.of(fileA, fileB)))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(fileStorage).delete("submissions/42/uuidA.txt");
        verify(fileStorage).delete("submissions/42/uuidB.txt");
    }

    @Test
    void flushFailureAfterSuccessfulSaveAllStillDeletesOrphanedS3Objects() {
        givenStudentAndAssignment(3L, false, OffsetDateTime.now().plusDays(1));
        MultipartFile file = multipartFile("a.txt", 10);
        StoredFile stored = new StoredFile("a.txt", "uuidA.txt", "submissions/42/uuidA.txt", "txt", 1L);
        when(fileStorage.upload(eq(file), startsWith("submissions/"))).thenReturn(stored);
        // saveAll() succeeds (e.g. a deferred constraint), but the actual INSERT only fails at flush time.
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("boom"))
                .when(submissionAttachmentRepository).flush();

        assertThatThrownBy(() -> service.submit(1L, 3L, request(null), List.of(file)))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(attachmentRepository).saveAll(any());
        verify(submissionAttachmentRepository).saveAll(any());
        verify(fileStorage).delete("submissions/42/uuidA.txt");
    }

    @Test
    void deletionFailureDuringCompensationDoesNotHideOriginalException() {
        givenStudentAndAssignment(3L, false, OffsetDateTime.now().plusDays(1));
        MultipartFile file = multipartFile("a.txt", 10);
        StoredFile stored = new StoredFile("a.txt", "uuidA.txt", "submissions/42/uuidA.txt", "txt", 1L);
        when(fileStorage.upload(eq(file), startsWith("submissions/"))).thenReturn(stored);
        when(attachmentRepository.saveAll(any())).thenThrow(new DataIntegrityViolationException("boom"));
        doThrow(new RuntimeException("s3 unreachable")).when(fileStorage).delete(anyString());

        assertThatThrownBy(() -> service.submit(1L, 3L, request(null), List.of(file)))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(fileStorage, times(3)).delete("submissions/42/uuidA.txt");
    }

    @Test
    void compensateRetriesS3DeleteAndSucceedsWithinAttemptBudget() {
        givenStudentAndAssignment(3L, false, OffsetDateTime.now().plusDays(1));
        MultipartFile file = multipartFile("a.txt", 10);
        StoredFile stored = new StoredFile("a.txt", "uuidA.txt", "submissions/42/uuidA.txt", "txt", 1L);
        when(fileStorage.upload(eq(file), startsWith("submissions/"))).thenReturn(stored);
        when(attachmentRepository.saveAll(any())).thenThrow(new DataIntegrityViolationException("boom"));
        doThrow(new RuntimeException("transient"))
                .doThrow(new RuntimeException("transient"))
                .doNothing()
                .when(fileStorage).delete("submissions/42/uuidA.txt");

        assertThatThrownBy(() -> service.submit(1L, 3L, request(null), List.of(file)))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(fileStorage, times(3)).delete("submissions/42/uuidA.txt");
    }

    @Test
    void getMySubmissionReturnsSubmissionWithFiles() {
        Member student = student(3L);
        Assignment assignment = assignment(false, OffsetDateTime.now().plusDays(1));
        Submission submission = Submission.create(assignment, student, "내용", false);
        setId(submission, 5L);
        stubApprovedMember(3L, student);
        when(submissionRepository.findByAssignmentIdAndStudentId(1L, 3L)).thenReturn(Optional.of(submission));
        Attachment attachment = Attachment.create("a.txt", "uuid.txt", "submissions/5/uuid.txt", "txt", 1L);
        SubmissionAttachment link = SubmissionAttachment.create(submission, attachment);
        when(submissionAttachmentRepository.findWithAttachmentBySubmissionId(5L)).thenReturn(List.of(link));

        var response = service.getMySubmission(1L, 3L);

        assertThat(response.getSubmissionId()).isEqualTo(5L);
        assertThat(response.getFiles()).hasSize(1);
        assertThat(response.getFiles().get(0).getOriginalName()).isEqualTo("a.txt");
    }

    @Test
    void getMySubmissionReturnsNotFoundWhenAbsent() {
        Member student = student(3L);
        stubApprovedMember(3L, student);
        when(submissionRepository.findByAssignmentIdAndStudentId(1L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMySubmission(1L, 3L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getMySubmissionsGroupsAttachmentsBySubmissionAndLeavesOthersEmpty() {
        Member student = student(3L);
        stubApprovedMember(3L, student);
        SubmissionListResponse withFile = new SubmissionListResponse(10L, 1L, "과제1", "내용1", false,
                OffsetDateTime.now(), OffsetDateTime.now());
        SubmissionListResponse withoutFile = new SubmissionListResponse(20L, 2L, "과제2", "내용2", true,
                OffsetDateTime.now(), OffsetDateTime.now());
        Pageable requested = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "title"));
        Pageable pageable = PageRequest.of(0, 20);
        Page<SubmissionListResponse> page = new PageImpl<>(List.of(withFile, withoutFile), pageable, 2);
        when(submissionRepository.findMySubmissionPageDueAtDesc(3L, false, pageable)).thenReturn(page);
        Attachment attachmentEntity = attachment("a.txt", "submissions/10/a.txt");
        SubmissionAttachment link = linkTo(10L, attachmentEntity);
        when(submissionAttachmentRepository.findWithAttachmentBySubmissionIdIn(List.of(10L, 20L)))
                .thenReturn(List.of(link));

        Page<SubmissionListResponse> result = service.getMySubmissions(
                3L, false, MySubmissionSort.DUE_AT_DESC, requested);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getAttachments()).extracting(SubmissionFileResponse::getOriginalName)
                .containsExactly("a.txt");
        assertThat(result.getContent().get(0).getFileNames()).containsExactly("a.txt");
        assertThat(result.getContent().get(1).getAttachments()).isEmpty();
        assertThat(result.getContent().get(1).getFileNames()).isEmpty();
        verify(submissionAttachmentRepository).findWithAttachmentBySubmissionIdIn(List.of(10L, 20L));
    }

    @Test
    void getMySubmissionsMixedPageQueriesAttachmentsWithOnlyNonNullSubmissionIds() {
        Member student = student(3L);
        stubApprovedMember(3L, student);
        SubmissionListResponse submitted = listRow(10L, 1L);
        SubmissionListResponse notSubmitted = listRow(null, 2L);
        Pageable pageable = PageRequest.of(0, 20);
        when(submissionRepository.findMySubmissionPageDueAtDesc(3L, true, pageable))
                .thenReturn(new PageImpl<>(List.of(submitted, notSubmitted), pageable, 2));
        Attachment attachmentEntity = attachment("a.txt", "submissions/10/a.txt");
        SubmissionAttachment attachmentLink = linkTo(10L, attachmentEntity);
        when(submissionAttachmentRepository.findWithAttachmentBySubmissionIdIn(List.of(10L)))
                .thenReturn(List.of(attachmentLink));

        Page<SubmissionListResponse> result = service.getMySubmissions(
                3L, true, MySubmissionSort.DUE_AT_DESC, pageable);

        assertThat(result.getContent()).containsExactly(submitted, notSubmitted);
        assertThat(result.getTotalElements()).isEqualTo(2);
        verify(submissionAttachmentRepository).findWithAttachmentBySubmissionIdIn(List.of(10L));
        assertThat(notSubmitted.getAttachments()).isEmpty();
        assertThat(notSubmitted.getFileNames()).isEmpty();
    }

    @Test
    void getMySubmissionsAllNotSubmittedPageSkipsAttachmentQueryAndKeepsPageMetadata() {
        Member student = student(3L);
        stubApprovedMember(3L, student);
        SubmissionListResponse first = listRow(null, 1L);
        SubmissionListResponse second = listRow(null, 2L);
        Pageable pageable = PageRequest.of(0, 2);
        when(submissionRepository.findMySubmissionPageDueAtDesc(3L, true, pageable))
                .thenReturn(new PageImpl<>(List.of(first, second), pageable, 5));

        Page<SubmissionListResponse> result = service.getMySubmissions(
                3L, true, MySubmissionSort.DUE_AT_DESC, pageable);

        assertThat(result.getContent()).containsExactly(first, second);
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(first.getAttachments()).isEmpty();
        assertThat(first.getFileNames()).isEmpty();
        assertThat(second.getAttachments()).isEmpty();
        assertThat(second.getFileNames()).isEmpty();
        verify(submissionAttachmentRepository, never()).findWithAttachmentBySubmissionIdIn(any());
    }

    @Test
    void getMySubmissionsSkipsAttachmentQueryWhenPageIsEmpty() {
        Member student = student(3L);
        stubApprovedMember(3L, student);
        Pageable pageable = PageRequest.of(0, 20);
        when(submissionRepository.findMySubmissionPageDueAtDesc(3L, false, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<SubmissionListResponse> result = service.getMySubmissions(
                3L, false, MySubmissionSort.DUE_AT_DESC, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(submissionAttachmentRepository, never()).findWithAttachmentBySubmissionIdIn(any());
    }

    @Test
    void getMySubmissionsForbiddenForAdmin() {
        Member admin = mock(Member.class);
        when(admin.getRole()).thenReturn(MemberRole.ADMIN);
        stubApprovedMember(7L, admin);

        assertThatThrownBy(() -> service.getMySubmissions(
                7L, false, MySubmissionSort.DUE_AT_DESC, PageRequest.of(0, 20)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(submissionRepository, never()).findMySubmissionPageDueAtDesc(any(), anyBoolean(), any());
    }

    @Test
    void resubmitReplacesTextOnlyWhenNoNewFiles() {
        Member student = student(3L);
        Assignment assignment = assignment(false, OffsetDateTime.now().plusDays(1));
        Submission submission = existingSubmission(5L, student, assignment, "old text", false);
        Attachment oldAttachment = attachment("old.txt", "submissions/5/old.txt");
        SubmissionAttachment oldLink = SubmissionAttachment.create(submission, oldAttachment);
        stubApprovedMember(3L, student);
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(submissionRepository.findByAssignmentIdAndStudentId(1L, 3L)).thenReturn(Optional.of(submission));
        when(submissionAttachmentRepository.findWithAttachmentBySubmissionId(5L)).thenReturn(List.of(oldLink));

        var response = service.resubmit(1L, 3L, request("new text"), null);

        assertThat(response.getTextContent()).isEqualTo("new text");
        assertThat(response.getFiles()).isEmpty();
        verify(submissionAttachmentRepository).deleteAll(List.of(oldLink));
        verify(attachmentRepository).deleteAll(List.of(oldAttachment));
        verify(fileStorage).delete("submissions/5/old.txt");
        verify(fileStorage, never()).upload(any(), anyString());
    }

    @Test
    void resubmitReplacesFilesAndDeletesOldS3Objects() {
        Member student = student(3L);
        Assignment assignment = assignment(false, OffsetDateTime.now().plusDays(1));
        Submission submission = existingSubmission(5L, student, assignment, "old text", false);
        Attachment oldAttachment = attachment("old.txt", "submissions/5/old.txt");
        SubmissionAttachment oldLink = SubmissionAttachment.create(submission, oldAttachment);
        stubApprovedMember(3L, student);
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(submissionRepository.findByAssignmentIdAndStudentId(1L, 3L)).thenReturn(Optional.of(submission));
        when(submissionAttachmentRepository.findWithAttachmentBySubmissionId(5L)).thenReturn(List.of(oldLink));
        MultipartFile newFile = multipartFile("new.txt", 10);
        StoredFile storedNew = new StoredFile("new.txt", "uuidNew.txt", "submissions/5/uuidNew.txt", "txt", 1L);
        when(fileStorage.upload(eq(newFile), startsWith("submissions/"))).thenReturn(storedNew);

        var response = service.resubmit(1L, 3L, request(null), List.of(newFile));

        assertThat(response.getFiles()).hasSize(1);
        assertThat(response.getFiles().get(0).getOriginalName()).isEqualTo("new.txt");
        verify(fileStorage).upload(eq(newFile), startsWith("submissions/"));
        verify(fileStorage).delete("submissions/5/old.txt");
        verify(fileStorage, never()).delete("submissions/5/uuidNew.txt");
    }

    @Test
    void resubmitWithoutExistingSubmissionReturnsNotFound() {
        givenStudentAndAssignment(3L, false, OffsetDateTime.now().plusDays(1));
        when(submissionRepository.findByAssignmentIdAndStudentId(1L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resubmit(1L, 3L, request("내용"), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void resubmitBlockedWhenLateNotAllowed() {
        Member student = student(3L);
        Assignment assignment = assignment(false, OffsetDateTime.now().minusDays(1));
        Submission submission = existingSubmission(5L, student, assignment, "old", false);
        stubApprovedMember(3L, student);
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(submissionRepository.findByAssignmentIdAndStudentId(1L, 3L)).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> service.resubmit(1L, 3L, request("내용"), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(submissionAttachmentRepository, never()).deleteAll(any());
    }

    @Test
    void resubmitDbFailureCompensatesOnlyNewlyUploadedFiles() {
        Member student = student(3L);
        Assignment assignment = assignment(false, OffsetDateTime.now().plusDays(1));
        Submission submission = existingSubmission(5L, student, assignment, "old", false);
        Attachment oldAttachment = attachment("old.txt", "submissions/5/old.txt");
        SubmissionAttachment oldLink = SubmissionAttachment.create(submission, oldAttachment);
        stubApprovedMember(3L, student);
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(submissionRepository.findByAssignmentIdAndStudentId(1L, 3L)).thenReturn(Optional.of(submission));
        when(submissionAttachmentRepository.findWithAttachmentBySubmissionId(5L)).thenReturn(List.of(oldLink));
        MultipartFile newFile = multipartFile("new.txt", 10);
        StoredFile storedNew = new StoredFile("new.txt", "uuidNew.txt", "submissions/5/uuidNew.txt", "txt", 1L);
        when(fileStorage.upload(eq(newFile), startsWith("submissions/"))).thenReturn(storedNew);
        when(submissionAttachmentRepository.saveAll(any())).thenThrow(new DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> service.resubmit(1L, 3L, request(null), List.of(newFile)))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(fileStorage).delete("submissions/5/uuidNew.txt");
        verify(fileStorage, never()).delete("submissions/5/old.txt");
        verify(attachmentRepository, never()).deleteAll(any());
    }

    @Test
    void deleteSubmissionRemovesAttachmentsAndSubmission() {
        Member student = student(3L);
        Assignment assignment = assignment(false, OffsetDateTime.now().plusDays(1));
        Submission submission = existingSubmission(5L, student, assignment, "text", false);
        Attachment attachment1 = attachment("a.txt", "submissions/5/a.txt");
        Attachment attachment2 = attachment("b.txt", "submissions/5/b.txt");
        SubmissionAttachment link1 = SubmissionAttachment.create(submission, attachment1);
        SubmissionAttachment link2 = SubmissionAttachment.create(submission, attachment2);
        stubApprovedMember(3L, student);
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(submissionRepository.findByAssignmentIdAndStudentId(1L, 3L)).thenReturn(Optional.of(submission));
        when(submissionAttachmentRepository.findWithAttachmentBySubmissionId(5L))
                .thenReturn(List.of(link1, link2));

        service.deleteSubmission(1L, 3L);

        verify(submissionAttachmentRepository).deleteAll(List.of(link1, link2));
        verify(attachmentRepository).deleteAll(List.of(attachment1, attachment2));
        verify(submissionRepository).delete(submission);
        verify(fileStorage).delete("submissions/5/a.txt");
        verify(fileStorage).delete("submissions/5/b.txt");
    }

    @Test
    void deleteSubmissionRetriesS3DeleteAndSucceedsWithinAttemptBudget() {
        Member student = student(3L);
        Assignment assignment = assignment(false, OffsetDateTime.now().plusDays(1));
        Submission submission = existingSubmission(5L, student, assignment, "text", false);
        Attachment attachmentEntity = attachment("a.txt", "submissions/5/a.txt");
        SubmissionAttachment link = SubmissionAttachment.create(submission, attachmentEntity);
        stubApprovedMember(3L, student);
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(submissionRepository.findByAssignmentIdAndStudentId(1L, 3L)).thenReturn(Optional.of(submission));
        when(submissionAttachmentRepository.findWithAttachmentBySubmissionId(5L)).thenReturn(List.of(link));
        doThrow(new RuntimeException("transient"))
                .doThrow(new RuntimeException("transient"))
                .doNothing()
                .when(fileStorage).delete("submissions/5/a.txt");

        service.deleteSubmission(1L, 3L);

        verify(fileStorage, times(3)).delete("submissions/5/a.txt");
    }

    @Test
    void deleteSubmissionGivesUpS3DeleteAfterMaxAttemptsWithoutFailingTheRequest() {
        Member student = student(3L);
        Assignment assignment = assignment(false, OffsetDateTime.now().plusDays(1));
        Submission submission = existingSubmission(5L, student, assignment, "text", false);
        Attachment attachmentEntity = attachment("a.txt", "submissions/5/a.txt");
        SubmissionAttachment link = SubmissionAttachment.create(submission, attachmentEntity);
        stubApprovedMember(3L, student);
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(submissionRepository.findByAssignmentIdAndStudentId(1L, 3L)).thenReturn(Optional.of(submission));
        when(submissionAttachmentRepository.findWithAttachmentBySubmissionId(5L)).thenReturn(List.of(link));
        doThrow(new RuntimeException("permanent")).when(fileStorage).delete("submissions/5/a.txt");

        service.deleteSubmission(1L, 3L);

        verify(fileStorage, times(3)).delete("submissions/5/a.txt");
        verify(submissionRepository).delete(submission);
    }

    @Test
    void deleteSubmissionMissingReturnsNotFound() {
        Member student = student(3L);
        stubApprovedMember(3L, student);
        when(submissionRepository.findByAssignmentIdAndStudentId(1L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteSubmission(1L, 3L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getDownloadUrlSucceedsForOwner() {
        Member student = student(3L);
        Assignment assignment = assignment(false, OffsetDateTime.now().plusDays(1));
        Submission submission = existingSubmission(5L, student, assignment, "text", false);
        Attachment targetAttachment = attachment("a.txt", "submissions/5/a.txt");
        SubmissionAttachment link = SubmissionAttachment.create(submission, targetAttachment);
        stubApprovedMember(3L, student);
        givenLockedSubmission(assignment, submission);
        when(submissionAttachmentRepository.findWithSubmissionAndAttachmentByAttachmentId(10L))
                .thenReturn(Optional.of(link));
        when(fileStorage.createDownloadUrl("submissions/5/a.txt"))
                .thenReturn(new DownloadUrl("https://example.com/signed", 347L));

        var response = service.getDownloadUrl(10L, 3L);

        assertThat(response.getDownloadUrl()).isEqualTo("https://example.com/signed");
        assertThat(response.getExpiresIn()).isEqualTo(347L);
        assertThat(response.getOriginalName()).isEqualTo("a.txt");
    }

    @Test
    void getDownloadUrlSucceedsForAdmin() {
        Member student = student(3L);
        Assignment assignment = assignment(false, OffsetDateTime.now().plusDays(1));
        Submission submission = existingSubmission(5L, student, assignment, "text", false);
        Attachment targetAttachment = attachment("a.txt", "submissions/5/a.txt");
        SubmissionAttachment link = SubmissionAttachment.create(submission, targetAttachment);
        Member admin = mock(Member.class);
        when(admin.getId()).thenReturn(7L);
        when(admin.getRole()).thenReturn(MemberRole.ADMIN);
        stubApprovedMember(7L, admin);
        when(submissionAttachmentRepository.findWithSubmissionAndAttachmentByAttachmentId(10L))
                .thenReturn(Optional.of(link));
        when(fileStorage.createDownloadUrl("submissions/5/a.txt"))
                .thenReturn(new DownloadUrl("https://example.com/signed", 347L));

        var response = service.getDownloadUrl(10L, 7L);

        assertThat(response.getDownloadUrl()).isEqualTo("https://example.com/signed");
    }

    @Test
    void getDownloadUrlForbiddenForOtherStudent() {
        Member owner = student(3L);
        Assignment assignment = assignment(false, OffsetDateTime.now().plusDays(1));
        Submission submission = existingSubmission(5L, owner, assignment, "text", false);
        Attachment targetAttachment = attachment("a.txt", "submissions/5/a.txt");
        SubmissionAttachment link = SubmissionAttachment.create(submission, targetAttachment);
        Member other = student(8L);
        stubApprovedMember(8L, other);
        when(submissionAttachmentRepository.findWithSubmissionAndAttachmentByAttachmentId(10L))
                .thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.getDownloadUrl(10L, 8L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(fileStorage, never()).createDownloadUrl(anyString());
    }

    @Test
    void getDownloadUrlMissingAttachmentReturnsNotFound() {
        Member student = student(3L);
        stubApprovedMember(3L, student);
        when(submissionAttachmentRepository.findWithSubmissionAndAttachmentByAttachmentId(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDownloadUrl(99L, 3L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(fileStorage, never()).createDownloadUrl(anyString());
    }

    @Test
    void deleteAttachmentSucceedsWhenOtherFileRemains() {
        Member student = student(3L);
        Assignment assignment = assignment(false, OffsetDateTime.now().plusDays(1));
        Submission submission = existingSubmission(5L, student, assignment, null, false);
        Attachment targetAttachment = attachment("a.txt", "submissions/5/a.txt");
        SubmissionAttachment link = SubmissionAttachment.create(submission, targetAttachment);
        stubApprovedMember(3L, student);
        givenLockedSubmission(assignment, submission);
        when(submissionAttachmentRepository.findWithSubmissionAndAttachmentByAttachmentId(10L))
                .thenReturn(Optional.of(link));
        when(submissionAttachmentRepository.countBySubmissionId(5L)).thenReturn(2L);

        service.deleteAttachment(10L, 3L);

        verify(fileStorage).delete("submissions/5/a.txt");
        verify(submissionAttachmentRepository).delete(link);
        verify(attachmentRepository).delete(targetAttachment);
    }

    @Test
    void deleteAttachmentBlockedWhenResultingSubmissionWouldBeEmpty() {
        Member student = student(3L);
        Assignment assignment = assignment(false, OffsetDateTime.now().plusDays(1));
        Submission submission = existingSubmission(5L, student, assignment, null, false);
        Attachment targetAttachment = attachment("a.txt", "submissions/5/a.txt");
        SubmissionAttachment link = SubmissionAttachment.create(submission, targetAttachment);
        stubApprovedMember(3L, student);
        givenLockedSubmission(assignment, submission);
        when(submissionAttachmentRepository.findWithSubmissionAndAttachmentByAttachmentId(10L))
                .thenReturn(Optional.of(link));
        when(submissionAttachmentRepository.countBySubmissionId(5L)).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteAttachment(10L, 3L))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("DELETE");
                });
        verify(fileStorage, never()).delete(anyString());
        verify(submissionAttachmentRepository, never()).delete(any());
    }

    @Test
    void deleteAttachmentAllowedWhenTextRemains() {
        Member student = student(3L);
        Assignment assignment = assignment(false, OffsetDateTime.now().plusDays(1));
        Submission submission = existingSubmission(5L, student, assignment, "남은 텍스트", false);
        Attachment targetAttachment = attachment("a.txt", "submissions/5/a.txt");
        SubmissionAttachment link = SubmissionAttachment.create(submission, targetAttachment);
        stubApprovedMember(3L, student);
        givenLockedSubmission(assignment, submission);
        when(submissionAttachmentRepository.findWithSubmissionAndAttachmentByAttachmentId(10L))
                .thenReturn(Optional.of(link));
        when(submissionAttachmentRepository.countBySubmissionId(5L)).thenReturn(1L);

        service.deleteAttachment(10L, 3L);

        verify(fileStorage).delete("submissions/5/a.txt");
    }

    @Test
    void deleteAttachmentForbiddenForNonOwnerStudent() {
        Member owner = student(3L);
        Assignment assignment = assignment(false, OffsetDateTime.now().plusDays(1));
        Submission submission = existingSubmission(5L, owner, assignment, "text", false);
        Attachment targetAttachment = attachment("a.txt", "submissions/5/a.txt");
        SubmissionAttachment link = SubmissionAttachment.create(submission, targetAttachment);
        Member other = student(8L);
        stubApprovedMember(8L, other);
        when(submissionAttachmentRepository.findWithSubmissionAndAttachmentByAttachmentId(10L))
                .thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.deleteAttachment(10L, 8L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void deleteAttachmentForbiddenForAdmin() {
        Member admin = mock(Member.class);
        when(admin.getRole()).thenReturn(MemberRole.ADMIN);
        stubApprovedMember(7L, admin);

        assertThatThrownBy(() -> service.deleteAttachment(10L, 7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(submissionAttachmentRepository, never()).findWithSubmissionAndAttachmentByAttachmentId(any());
    }

    @Test
    void deleteAttachmentBlockedWhenLateNotAllowed() {
        Member student = student(3L);
        Assignment assignment = assignment(false, OffsetDateTime.now().minusDays(1));
        Submission submission = existingSubmission(5L, student, assignment, "text", true);
        Attachment targetAttachment = attachment("a.txt", "submissions/5/a.txt");
        SubmissionAttachment link = SubmissionAttachment.create(submission, targetAttachment);
        stubApprovedMember(3L, student);
        givenLockedSubmission(assignment, submission);
        when(submissionAttachmentRepository.findWithSubmissionAndAttachmentByAttachmentId(10L))
                .thenReturn(Optional.of(link));

        assertThatThrownBy(() -> service.deleteAttachment(10L, 3L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(fileStorage, never()).delete(anyString());
    }

    @Test
    void deleteAttachmentDoesNotTouchS3WhenDbDeleteFails() {
        Member student = student(3L);
        Assignment assignment = assignment(false, OffsetDateTime.now().plusDays(1));
        Submission submission = existingSubmission(5L, student, assignment, null, false);
        Attachment targetAttachment = attachment("a.txt", "submissions/5/a.txt");
        SubmissionAttachment link = SubmissionAttachment.create(submission, targetAttachment);
        stubApprovedMember(3L, student);
        givenLockedSubmission(assignment, submission);
        when(submissionAttachmentRepository.findWithSubmissionAndAttachmentByAttachmentId(10L))
                .thenReturn(Optional.of(link));
        when(submissionAttachmentRepository.countBySubmissionId(5L)).thenReturn(2L);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("boom"))
                .when(submissionAttachmentRepository).flush();

        assertThatThrownBy(() -> service.deleteAttachment(10L, 3L))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(fileStorage, never()).delete(anyString());
    }

    @Test
    void deleteAttachmentMissingReturnsNotFound() {
        Member student = student(3L);
        stubApprovedMember(3L, student);
        when(submissionAttachmentRepository.findWithSubmissionAndAttachmentByAttachmentId(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteAttachment(99L, 3L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private void givenStudentAndAssignment(Long memberId, boolean allowLate, OffsetDateTime dueAt) {
        Member student = student(memberId);
        Assignment assignment = assignment(allowLate, dueAt);
        stubApprovedMember(memberId, student);
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
    }

    private void givenLockedSubmission(Assignment assignment, Submission submission) {
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(submissionRepository.findByAssignmentIdAndStudentId(1L, 3L)).thenReturn(Optional.of(submission));
    }

    private SubmissionCreateRequest request(String textContent) {
        SubmissionCreateRequest request = mock(SubmissionCreateRequest.class);
        when(request.getTextContent()).thenReturn(textContent);
        return request;
    }

    private SubmissionListResponse listRow(Long submissionId, Long assignmentId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new SubmissionListResponse(submissionId, assignmentId, "assignment-" + assignmentId,
                now.plusDays(1), false, submissionId == null ? null : "text", false,
                submissionId == null ? null : now, submissionId == null ? null : now);
    }

    private Member student(Long id) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(id);
        when(member.getRole()).thenReturn(MemberRole.STUDENT);
        return member;
    }

    private Assignment assignment(boolean allowLate, OffsetDateTime dueAt) {
        Assignment assignment = mock(Assignment.class);
        when(assignment.getId()).thenReturn(1L);
        when(assignment.getDueAt()).thenReturn(dueAt.withOffsetSameInstant(ZoneOffset.UTC));
        when(assignment.isAllowLateSubmission()).thenReturn(allowLate);
        return assignment;
    }

    private Submission existingSubmission(Long id, Member student, Assignment assignment, String textContent,
                                          boolean late) {
        Submission submission = Submission.create(assignment, student, textContent, late);
        setId(submission, id);
        return submission;
    }

    private Attachment attachment(String originalName, String storageKey) {
        return Attachment.create(originalName, "stored-" + originalName, storageKey, "txt", 1L);
    }

    private SubmissionAttachment linkTo(Long submissionId, Attachment attachmentEntity) {
        Submission submission = mock(Submission.class);
        when(submission.getId()).thenReturn(submissionId);
        return SubmissionAttachment.create(submission, attachmentEntity);
    }

    private MultipartFile multipartFile(String originalName, long size) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(size);
        when(file.getOriginalFilename()).thenReturn(originalName);
        return file;
    }

    private void useClock(Clock clock) {
        service = new SubmissionService(submissionRepository, assignmentRepository, memberGuard,
                attachmentRepository, submissionAttachmentRepository, fileStorage, clock,
                storageLifecycle, transactions, new SubmissionStatusCalculator());
    }

    private void stubApprovedMember(Long memberId, Member member) {
        when(memberGuard.requireMember(memberId)).thenReturn(member);
        if (member.getRole() == MemberRole.STUDENT) {
            when(memberGuard.requireStudent(memberId)).thenReturn(member);
        } else {
            when(memberGuard.requireStudent(memberId))
                    .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "학생만 이용할 수 있는 기능입니다."));
        }
    }

    private void stubMissingMember(Long memberId) {
        BusinessException notFound = new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다.");
        when(memberGuard.requireMember(memberId)).thenThrow(notFound);
        when(memberGuard.requireStudent(memberId)).thenThrow(notFound);
    }

    private void setId(Submission submission, Long id) {
        try {
            Field field = Submission.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(submission, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
