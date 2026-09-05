package com.sat.lms.submission.service;

import com.sat.lms.assignment.entity.Assignment;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.service.MemberGuard;
import com.sat.lms.submission.dto.SubmissionCommentResponse;
import com.sat.lms.submission.entity.Submission;
import com.sat.lms.submission.entity.SubmissionComment;
import com.sat.lms.submission.repository.SubmissionCommentRepository;
import com.sat.lms.submission.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubmissionCommentServiceTest {
    SubmissionRepository submissionRepository;
    SubmissionCommentRepository submissionCommentRepository;
    MemberGuard memberGuard;
    SubmissionCommentService service;

    @BeforeEach
    void setUp() {
        submissionRepository = mock(SubmissionRepository.class);
        submissionCommentRepository = mock(SubmissionCommentRepository.class);
        memberGuard = mock(MemberGuard.class);
        service = new SubmissionCommentService(submissionRepository, submissionCommentRepository, memberGuard);

        when(submissionCommentRepository.save(any())).thenAnswer(invocation -> {
            SubmissionComment comment = invocation.getArgument(0);
            setId(comment, 100L);
            return comment;
        });
    }

    @Test
    void ownerCanCreateComment() {
        Member owner = member(1L, MemberRole.STUDENT);
        Submission submission = submission(1L, owner);
        when(memberGuard.requireMember(1L)).thenReturn(owner);
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(submission));

        SubmissionCommentResponse response = service.create(1L, 1L, "댓글입니다.");

        assertThat(response.getContent()).isEqualTo("댓글입니다.");
        assertThat(response.getAuthorName()).isEqualTo(owner.getName());
        assertThat(response.getCommentId()).isEqualTo(100L);
    }

    @Test
    void adminCanCreateCommentOnAnySubmission() {
        Member owner = member(1L, MemberRole.STUDENT);
        Member admin = member(2L, MemberRole.ADMIN);
        Submission submission = submission(1L, owner);
        when(memberGuard.requireMember(2L)).thenReturn(admin);
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(submission));

        SubmissionCommentResponse response = service.create(1L, 2L, "관리자 댓글");

        assertThat(response.getAuthorRole()).isEqualTo("ADMIN");
    }

    @Test
    void otherStudentCannotCreateCommentOnSomeoneElsesSubmission() {
        Member owner = member(1L, MemberRole.STUDENT);
        Member other = member(3L, MemberRole.STUDENT);
        Submission submission = submission(1L, owner);
        when(memberGuard.requireMember(3L)).thenReturn(other);
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(submission));

        assertBusinessException(() -> service.create(1L, 3L, "댓글"), HttpStatus.FORBIDDEN);
        verify(submissionCommentRepository, never()).save(any());
    }

    @Test
    void creatingCommentOnMissingSubmissionThrowsNotFound() {
        when(memberGuard.requireMember(1L)).thenReturn(member(1L, MemberRole.STUDENT));
        when(submissionRepository.findById(1L)).thenReturn(Optional.empty());

        assertBusinessException(() -> service.create(1L, 1L, "댓글"), HttpStatus.NOT_FOUND);
    }

    @Test
    void ownerCanListComments() {
        Member owner = member(1L, MemberRole.STUDENT);
        Submission submission = submission(1L, owner);
        SubmissionComment comment = SubmissionComment.create(submission, owner, "댓글");
        setId(comment, 5L);
        when(memberGuard.requireMember(1L)).thenReturn(owner);
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(submission));
        when(submissionCommentRepository.findWithAuthorBySubmissionId(any(), any()))
                .thenReturn(new PageImpl<>(java.util.List.of(comment)));

        Page<SubmissionCommentResponse> page = service.getComments(1L, 1L, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getCommentId()).isEqualTo(5L);
    }

    @Test
    void otherStudentCannotListCommentsOnSomeoneElsesSubmission() {
        Member owner = member(1L, MemberRole.STUDENT);
        Member other = member(3L, MemberRole.STUDENT);
        Submission submission = submission(1L, owner);
        when(memberGuard.requireMember(3L)).thenReturn(other);
        when(submissionRepository.findById(1L)).thenReturn(Optional.of(submission));

        assertBusinessException(() -> service.getComments(1L, 3L, PageRequest.of(0, 20)), HttpStatus.FORBIDDEN);
    }

    @Test
    void authorCanUpdateOwnComment() {
        Member author = member(1L, MemberRole.STUDENT);
        Submission submission = submission(1L, author);
        SubmissionComment comment = SubmissionComment.create(submission, author, "원본");
        setId(comment, 5L);
        when(memberGuard.requireMember(1L)).thenReturn(author);
        when(submissionCommentRepository.findById(5L)).thenReturn(Optional.of(comment));

        SubmissionCommentResponse response = service.update(5L, 1L, "수정됨");

        assertThat(response.getContent()).isEqualTo("수정됨");
    }

    @Test
    void nonAuthorCannotUpdateComment() {
        Member author = member(1L, MemberRole.STUDENT);
        Member admin = member(2L, MemberRole.ADMIN);
        Submission submission = submission(1L, author);
        SubmissionComment comment = SubmissionComment.create(submission, author, "원본");
        setId(comment, 5L);
        when(memberGuard.requireMember(2L)).thenReturn(admin);
        when(submissionCommentRepository.findById(5L)).thenReturn(Optional.of(comment));

        assertBusinessException(() -> service.update(5L, 2L, "수정 시도"), HttpStatus.FORBIDDEN);
    }

    @Test
    void updatingMissingCommentThrowsNotFound() {
        when(memberGuard.requireMember(1L)).thenReturn(member(1L, MemberRole.STUDENT));
        when(submissionCommentRepository.findById(5L)).thenReturn(Optional.empty());

        assertBusinessException(() -> service.update(5L, 1L, "수정"), HttpStatus.NOT_FOUND);
    }

    @Test
    void authorCanDeleteOwnComment() {
        Member author = member(1L, MemberRole.STUDENT);
        Submission submission = submission(1L, author);
        SubmissionComment comment = SubmissionComment.create(submission, author, "원본");
        setId(comment, 5L);
        when(memberGuard.requireMember(1L)).thenReturn(author);
        when(submissionCommentRepository.findById(5L)).thenReturn(Optional.of(comment));

        service.delete(5L, 1L);

        verify(submissionCommentRepository).delete(comment);
    }

    @Test
    void adminCanDeleteAnyonesComment() {
        Member author = member(1L, MemberRole.STUDENT);
        Member admin = member(2L, MemberRole.ADMIN);
        Submission submission = submission(1L, author);
        SubmissionComment comment = SubmissionComment.create(submission, author, "원본");
        setId(comment, 5L);
        when(memberGuard.requireMember(2L)).thenReturn(admin);
        when(submissionCommentRepository.findById(5L)).thenReturn(Optional.of(comment));

        service.delete(5L, 2L);

        verify(submissionCommentRepository).delete(comment);
    }

    @Test
    void nonAuthorNonAdminCannotDeleteComment() {
        Member author = member(1L, MemberRole.STUDENT);
        Member other = member(3L, MemberRole.STUDENT);
        Submission submission = submission(1L, author);
        SubmissionComment comment = SubmissionComment.create(submission, author, "원본");
        setId(comment, 5L);
        when(memberGuard.requireMember(3L)).thenReturn(other);
        when(submissionCommentRepository.findById(5L)).thenReturn(Optional.of(comment));

        assertBusinessException(() -> service.delete(5L, 3L), HttpStatus.FORBIDDEN);
        verify(submissionCommentRepository, never()).delete(any(SubmissionComment.class));
    }

    @Test
    void deletingMissingCommentThrowsNotFound() {
        when(memberGuard.requireMember(1L)).thenReturn(member(1L, MemberRole.STUDENT));
        when(submissionCommentRepository.findById(5L)).thenReturn(Optional.empty());

        assertBusinessException(() -> service.delete(5L, 1L), HttpStatus.NOT_FOUND);
    }

    private Member member(Long id, MemberRole role) {
        Member member = role == MemberRole.ADMIN
                ? Member.createStudent("admin" + id, "관리자" + id, "hash")
                : Member.createStudent("student" + id, "학생" + id, "hash");
        setId(member, id);
        if (role == MemberRole.ADMIN) setField(member, "role", MemberRole.ADMIN);
        return member;
    }

    private Submission submission(Long id, Member student) {
        Assignment assignment = mock(Assignment.class);
        Submission submission = Submission.create(assignment, student, "제출 내용", false);
        setId(submission, id);
        return submission;
    }

    private void assertBusinessException(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getStatus())
                .isEqualTo(status);
    }

    private void setId(Object entity, Long id) {
        setField(entity, "id", id);
    }

    private void setField(Object entity, String fieldName, Object value) {
        try {
            Field field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }
}
