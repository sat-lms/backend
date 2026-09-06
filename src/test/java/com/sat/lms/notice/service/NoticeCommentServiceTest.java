package com.sat.lms.notice.service;

import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.service.MemberGuard;
import com.sat.lms.notice.dto.NoticeCommentResponse;
import com.sat.lms.notice.entity.Notice;
import com.sat.lms.notice.entity.NoticeComment;
import com.sat.lms.notice.repository.NoticeCommentRepository;
import com.sat.lms.notice.repository.NoticeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoticeCommentServiceTest {
    NoticeRepository noticeRepository;
    NoticeCommentRepository noticeCommentRepository;
    MemberGuard memberGuard;
    NoticeCommentService service;

    @BeforeEach
    void setUp() {
        noticeRepository = mock(NoticeRepository.class);
        noticeCommentRepository = mock(NoticeCommentRepository.class);
        memberGuard = mock(MemberGuard.class);
        service = new NoticeCommentService(noticeRepository, noticeCommentRepository, memberGuard);

        when(noticeCommentRepository.save(any())).thenAnswer(invocation -> {
            NoticeComment comment = invocation.getArgument(0);
            setId(comment, 100L);
            return comment;
        });
    }

    @Test
    void studentCanCreateComment() {
        Member student = member(1L, MemberRole.STUDENT);
        Notice notice = notice(1L);
        when(memberGuard.requireMember(1L)).thenReturn(student);
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(notice));

        NoticeCommentResponse response = service.create(1L, 1L, "댓글입니다.");

        assertThat(response.getContent()).isEqualTo("댓글입니다.");
        assertThat(response.getAuthorName()).isEqualTo(student.getName());
        assertThat(response.getAuthorRole()).isEqualTo("STUDENT");
        assertThat(response.getCommentId()).isEqualTo(100L);
    }

    @Test
    void adminCanCreateComment() {
        Member admin = member(2L, MemberRole.ADMIN);
        Notice notice = notice(1L);
        when(memberGuard.requireMember(2L)).thenReturn(admin);
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(notice));

        NoticeCommentResponse response = service.create(1L, 2L, "관리자 댓글");

        assertThat(response.getAuthorRole()).isEqualTo("ADMIN");
    }

    @Test
    void creatingCommentOnMissingNoticeThrowsNotFound() {
        when(memberGuard.requireMember(1L)).thenReturn(member(1L, MemberRole.STUDENT));
        when(noticeRepository.findById(1L)).thenReturn(Optional.empty());

        assertBusinessException(() -> service.create(1L, 1L, "댓글"), HttpStatus.NOT_FOUND);
        verify(noticeCommentRepository, never()).save(any());
    }

    @Test
    void anyAuthenticatedMemberCanListComments() {
        Member student = member(1L, MemberRole.STUDENT);
        Notice notice = notice(1L);
        NoticeComment comment = NoticeComment.create(notice, student, "댓글");
        setId(comment, 5L);
        when(memberGuard.requireMember(1L)).thenReturn(student);
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(notice));
        when(noticeCommentRepository.findWithAuthorByNoticeId(any(), any()))
                .thenReturn(new PageImpl<>(List.of(comment)));

        Page<NoticeCommentResponse> page = service.getComments(1L, 1L, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getCommentId()).isEqualTo(5L);
    }

    @Test
    void listingCommentsOnMissingNoticeThrowsNotFound() {
        when(memberGuard.requireMember(1L)).thenReturn(member(1L, MemberRole.STUDENT));
        when(noticeRepository.findById(1L)).thenReturn(Optional.empty());

        assertBusinessException(() -> service.getComments(1L, 1L, PageRequest.of(0, 20)), HttpStatus.NOT_FOUND);
    }

    @Test
    void authorCanUpdateOwnComment() {
        Member author = member(1L, MemberRole.STUDENT);
        Notice notice = notice(1L);
        NoticeComment comment = NoticeComment.create(notice, author, "원본");
        setId(comment, 5L);
        when(memberGuard.requireMember(1L)).thenReturn(author);
        when(noticeCommentRepository.findById(5L)).thenReturn(Optional.of(comment));

        NoticeCommentResponse response = service.update(5L, 1L, "수정됨");

        assertThat(response.getContent()).isEqualTo("수정됨");
    }

    @Test
    void nonAuthorCannotUpdateComment() {
        Member author = member(1L, MemberRole.STUDENT);
        Member other = member(3L, MemberRole.STUDENT);
        Notice notice = notice(1L);
        NoticeComment comment = NoticeComment.create(notice, author, "원본");
        setId(comment, 5L);
        when(memberGuard.requireMember(3L)).thenReturn(other);
        when(noticeCommentRepository.findById(5L)).thenReturn(Optional.of(comment));

        assertBusinessException(() -> service.update(5L, 3L, "수정 시도"), HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCannotUpdateSomeoneElsesComment() {
        Member author = member(1L, MemberRole.STUDENT);
        Member admin = member(2L, MemberRole.ADMIN);
        Notice notice = notice(1L);
        NoticeComment comment = NoticeComment.create(notice, author, "원본");
        setId(comment, 5L);
        when(memberGuard.requireMember(2L)).thenReturn(admin);
        when(noticeCommentRepository.findById(5L)).thenReturn(Optional.of(comment));

        assertBusinessException(() -> service.update(5L, 2L, "수정 시도"), HttpStatus.FORBIDDEN);
    }

    @Test
    void updatingMissingCommentThrowsNotFound() {
        when(memberGuard.requireMember(1L)).thenReturn(member(1L, MemberRole.STUDENT));
        when(noticeCommentRepository.findById(5L)).thenReturn(Optional.empty());

        assertBusinessException(() -> service.update(5L, 1L, "수정"), HttpStatus.NOT_FOUND);
    }

    @Test
    void authorCanDeleteOwnComment() {
        Member author = member(1L, MemberRole.STUDENT);
        Notice notice = notice(1L);
        NoticeComment comment = NoticeComment.create(notice, author, "원본");
        setId(comment, 5L);
        when(memberGuard.requireMember(1L)).thenReturn(author);
        when(noticeCommentRepository.findById(5L)).thenReturn(Optional.of(comment));

        service.delete(5L, 1L);

        verify(noticeCommentRepository).delete(comment);
    }

    @Test
    void adminCanDeleteAnyonesComment() {
        Member author = member(1L, MemberRole.STUDENT);
        Member admin = member(2L, MemberRole.ADMIN);
        Notice notice = notice(1L);
        NoticeComment comment = NoticeComment.create(notice, author, "원본");
        setId(comment, 5L);
        when(memberGuard.requireMember(2L)).thenReturn(admin);
        when(noticeCommentRepository.findById(5L)).thenReturn(Optional.of(comment));

        service.delete(5L, 2L);

        verify(noticeCommentRepository).delete(comment);
    }

    @Test
    void nonAuthorNonAdminCannotDeleteComment() {
        Member author = member(1L, MemberRole.STUDENT);
        Member other = member(3L, MemberRole.STUDENT);
        Notice notice = notice(1L);
        NoticeComment comment = NoticeComment.create(notice, author, "원본");
        setId(comment, 5L);
        when(memberGuard.requireMember(3L)).thenReturn(other);
        when(noticeCommentRepository.findById(5L)).thenReturn(Optional.of(comment));

        assertBusinessException(() -> service.delete(5L, 3L), HttpStatus.FORBIDDEN);
        verify(noticeCommentRepository, never()).delete(any(NoticeComment.class));
    }

    @Test
    void deletingMissingCommentThrowsNotFound() {
        when(memberGuard.requireMember(1L)).thenReturn(member(1L, MemberRole.STUDENT));
        when(noticeCommentRepository.findById(5L)).thenReturn(Optional.empty());

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

    private Notice notice(Long id) {
        Member admin = mock(Member.class);
        Notice notice = Notice.create(admin, "제목", "내용", false);
        setId(notice, id);
        return notice;
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
