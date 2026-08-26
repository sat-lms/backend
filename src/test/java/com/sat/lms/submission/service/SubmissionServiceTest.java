package com.sat.lms.submission.service;

import com.sat.lms.assignment.entity.Assignment;
import com.sat.lms.assignment.repository.AssignmentRepository;
import com.sat.lms.attachment.entity.Attachment;
import com.sat.lms.attachment.entity.SubmissionAttachment;
import com.sat.lms.attachment.repository.AttachmentRepository;
import com.sat.lms.attachment.repository.SubmissionAttachmentRepository;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.storage.FileStorage;
import com.sat.lms.global.storage.StoredFile;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.repository.MemberRepository;
import com.sat.lms.submission.dto.SubmissionCreateRequest;
import com.sat.lms.submission.entity.Submission;
import com.sat.lms.submission.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubmissionServiceTest {
    SubmissionRepository submissionRepository;
    AssignmentRepository assignmentRepository;
    MemberRepository memberRepository;
    AttachmentRepository attachmentRepository;
    SubmissionAttachmentRepository submissionAttachmentRepository;
    FileStorage fileStorage;
    SubmissionService service;

    @BeforeEach
    void setUp() {
        submissionRepository = mock(SubmissionRepository.class);
        assignmentRepository = mock(AssignmentRepository.class);
        memberRepository = mock(MemberRepository.class);
        attachmentRepository = mock(AttachmentRepository.class);
        submissionAttachmentRepository = mock(SubmissionAttachmentRepository.class);
        fileStorage = mock(FileStorage.class);
        service = new SubmissionService(submissionRepository, assignmentRepository, memberRepository,
                attachmentRepository, submissionAttachmentRepository, fileStorage);

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
        when(fileStorage.upload(eq(file), eq("submissions/42"))).thenReturn(
                new StoredFile("Member.java", "uuid.java", "submissions/42/uuid.java", "java", 1L));

        var response = service.submit(1L, 3L, request(null), List.of(file));

        assertThat(response.getFiles()).hasSize(1);
        assertThat(response.getFiles().get(0).getOriginalName()).isEqualTo("Member.java");
        verify(fileStorage).upload(file, "submissions/42");
        verify(attachmentRepository).saveAll(any());
        verify(submissionAttachmentRepository).saveAll(any());
    }

    @Test
    void studentCanSubmitTextAndFiles() {
        givenStudentAndAssignment(3L, false, OffsetDateTime.now().plusDays(1));
        MultipartFile file = multipartFile("결과.png", 2048);
        when(fileStorage.upload(eq(file), eq("submissions/42"))).thenReturn(
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
        MultipartFile file = multipartFile("virus.exe", 10);

        assertThatThrownBy(() -> service.submit(1L, 3L, request(null), List.of(file)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
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
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.submit(1L, 7L, request("내용"), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(assignmentRepository, never()).findById(any());
    }

    @Test
    void unknownMemberReturnsNotFound() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submit(1L, 99L, request("내용"), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void missingAssignmentReturnsNotFound() {
        Member student = student(3L);
        when(memberRepository.findById(3L)).thenReturn(Optional.of(student));
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
        when(fileStorage.upload(fileA, "submissions/42")).thenReturn(storedA);
        when(fileStorage.upload(fileB, "submissions/42")).thenReturn(storedB);
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
        when(fileStorage.upload(file, "submissions/42")).thenReturn(stored);
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
        when(fileStorage.upload(file, "submissions/42")).thenReturn(stored);
        when(attachmentRepository.saveAll(any())).thenThrow(new DataIntegrityViolationException("boom"));
        org.mockito.Mockito.doThrow(new RuntimeException("s3 unreachable")).when(fileStorage).delete(anyString());

        assertThatThrownBy(() -> service.submit(1L, 3L, request(null), List.of(file)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void getMySubmissionReturnsSubmissionWithFiles() {
        Member student = student(3L);
        Assignment assignment = assignment(false, OffsetDateTime.now().plusDays(1));
        Submission submission = Submission.create(assignment, student, "내용", false);
        setId(submission, 5L);
        when(memberRepository.findById(3L)).thenReturn(Optional.of(student));
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
        when(memberRepository.findById(3L)).thenReturn(Optional.of(student));
        when(submissionRepository.findByAssignmentIdAndStudentId(1L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMySubmission(1L, 3L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private void givenStudentAndAssignment(Long memberId, boolean allowLate, OffsetDateTime dueAt) {
        Member student = student(memberId);
        Assignment assignment = assignment(allowLate, dueAt);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(student));
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));
    }

    private SubmissionCreateRequest request(String textContent) {
        SubmissionCreateRequest request = mock(SubmissionCreateRequest.class);
        when(request.getTextContent()).thenReturn(textContent);
        return request;
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

    private MultipartFile multipartFile(String originalName, long size) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(size);
        when(file.getOriginalFilename()).thenReturn(originalName);
        return file;
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