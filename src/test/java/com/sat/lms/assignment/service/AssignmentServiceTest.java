package com.sat.lms.assignment.service;

import com.sat.lms.assignment.dto.AssignmentCreateRequest;
import com.sat.lms.assignment.dto.AssignmentUpdateRequest;
import com.sat.lms.assignment.entity.Assignment;
import com.sat.lms.assignment.repository.AssignmentRepository;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.repository.MemberRepository;
import com.sat.lms.submission.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssignmentServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-27T12:00:00Z");
    AssignmentRepository assignmentRepository;
    SubmissionRepository submissionRepository;
    MemberRepository memberRepository;
    AssignmentService service;

    @BeforeEach
    void setUp() {
        assignmentRepository = mock(AssignmentRepository.class);
        submissionRepository = mock(SubmissionRepository.class);
        memberRepository = mock(MemberRepository.class);
        service = new AssignmentService(assignmentRepository, submissionRepository, memberRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void adminCreatesAssignment() {
        Member admin = member(MemberRole.ADMIN);
        AssignmentCreateRequest request = mock(AssignmentCreateRequest.class);
        LocalDateTime dueAt = LocalDateTime.parse("2026-09-01T00:00:00");
        when(request.getTitle()).thenReturn(" 과제 ");
        when(request.getContent()).thenReturn(" 내용 ");
        when(request.getDueAt()).thenReturn(dueAt);
        when(request.getAllowLateSubmission()).thenReturn(true);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request, 7L);

        ArgumentCaptor<Assignment> captor = ArgumentCaptor.forClass(Assignment.class);
        verify(assignmentRepository).save(captor.capture());
        assertThat(captor.getValue().getAdmin()).isSameAs(admin);
        assertThat(captor.getValue().getTitle()).isEqualTo("과제");
        assertThat(captor.getValue().getContent()).isEqualTo("내용");
        assertThat(captor.getValue().getDueAt()).isEqualTo(OffsetDateTime.parse("2026-09-01T00:00:00+09:00"));
        assertThat(captor.getValue().isAllowLateSubmission()).isTrue();
    }

    @Test
    void futureDueAtIsSaved() {
        AssignmentCreateRequest request = createRequest(LocalDateTime.parse("2026-08-27T21:00:01"));
        Member admin = member(MemberRole.ADMIN);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request, 7L);

        ArgumentCaptor<Assignment> captor = ArgumentCaptor.forClass(Assignment.class);
        verify(assignmentRepository).save(captor.capture());
        assertThat(captor.getValue().getDueAt()).isEqualTo(OffsetDateTime.parse("2026-08-27T21:00:01+09:00"));
        assertThat(captor.getValue().getDueAt().toInstant()).isEqualTo(Instant.parse("2026-08-27T12:00:01Z"));
    }

    @Test
    void pastDueAtIsRejectedWithoutSaving() {
        assertInvalidCreateDueAt(LocalDateTime.parse("2026-08-27T20:59:59"));
    }

    @Test
    void currentDueAtIsRejectedWithoutSaving() {
        assertInvalidCreateDueAt(LocalDateTime.parse("2026-08-27T21:00:00"));
    }

    @Test
    void studentCannotCreateUpdateOrDelete() {
        Member student = member(MemberRole.STUDENT);
        when(memberRepository.findById(8L)).thenReturn(Optional.of(student));

        assertForbidden(() -> service.create(mock(AssignmentCreateRequest.class), 8L));
        assertForbidden(() -> service.update(1L, new AssignmentUpdateRequest(), 8L));
        assertForbidden(() -> service.delete(1L, 8L));
        verify(assignmentRepository, never()).delete(any());
    }

    @Test
    void authenticatedMemberCanListUsingAllowedSort() {
        Member student = member(MemberRole.STUDENT);
        when(memberRepository.findById(8L)).thenReturn(Optional.of(student));
        when(assignmentRepository.findAssignmentPage(any())).thenReturn(Page.empty());

        service.getAssignments(8L, 1, 5, "dueAt,asc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(assignmentRepository).findAssignmentPage(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        assertThat(captor.getValue().getSort().getOrderFor("dueAt").isAscending()).isTrue();
    }

    @Test
    void defaultAndAllAllowedSortFieldsAreAccepted() {
        Member student = member(MemberRole.STUDENT);
        when(memberRepository.findById(8L)).thenReturn(Optional.of(student));
        when(assignmentRepository.findAssignmentPage(any())).thenReturn(Page.empty());

        for (String field : new String[]{"createdAt", "updatedAt", "dueAt", "title"}) {
            service.getAssignments(8L, 0, 20, field + ",desc");
        }
        service.getAssignments(8L, 0, 20, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(assignmentRepository, org.mockito.Mockito.times(5)).findAssignmentPage(captor.capture());
        Pageable defaultPageable = captor.getAllValues().get(4);
        assertThat(defaultPageable.getSort().getOrderFor("dueAt").isAscending()).isTrue();
        assertThat(defaultPageable.getSort().getOrderFor("id").isAscending()).isTrue();
    }

    @Test
    void unsupportedSortFieldAndDirectionReturnBadRequest() {
        Member student = member(MemberRole.STUDENT);
        when(memberRepository.findById(8L)).thenReturn(Optional.of(student));

        assertBadRequest(() -> service.getAssignments(8L, 0, 20, "content,asc"));
        assertBadRequest(() -> service.getAssignments(8L, 0, 20, "dueAt,sideways"));
        assertBadRequest(() -> service.getAssignments(8L, 0, 20, " dueAt,asc"));
        assertBadRequest(() -> service.getAssignments(8L, 0, 20, "dueAt,asc,createdAt"));
        verify(assignmentRepository, never()).findAssignmentPage(any());
    }

    @Test
    void partialUpdateChangesOnlyProvidedFields() {
        Member admin = member(MemberRole.ADMIN);
        OffsetDateTime oldDueAt = OffsetDateTime.parse("2026-09-01T00:00:00Z");
        Assignment assignment = Assignment.create(admin, "기존 제목", "기존 내용", oldDueAt, true);
        AssignmentUpdateRequest request = new AssignmentUpdateRequest();
        request.setTitle(" 새 제목 ");
        request.setAllowLateSubmission(false);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));

        service.update(1L, request, 7L);

        assertThat(assignment.getTitle()).isEqualTo("새 제목");
        assertThat(assignment.getContent()).isEqualTo("기존 내용");
        assertThat(assignment.getDueAt()).isEqualTo(oldDueAt);
        assertThat(assignment.isAllowLateSubmission()).isFalse();
        verify(assignmentRepository).flush();
    }

    @Test
    void emptyOrNullPatchFieldsReturnBadRequest() {
        Member admin = member(MemberRole.ADMIN);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        assertBadRequest(() -> service.update(1L, new AssignmentUpdateRequest(), 7L));

        AssignmentUpdateRequest blankTitle = new AssignmentUpdateRequest();
        blankTitle.setTitle("   ");
        assertBadRequest(() -> service.update(1L, blankTitle, 7L));

        AssignmentUpdateRequest nullContent = new AssignmentUpdateRequest();
        nullContent.setContent(null);
        assertBadRequest(() -> service.update(1L, nullContent, 7L));

        AssignmentUpdateRequest nullDueAt = new AssignmentUpdateRequest();
        nullDueAt.setDueAt(null);
        assertBadRequest(() -> service.update(1L, nullDueAt, 7L));

        AssignmentUpdateRequest nullLateAllowance = new AssignmentUpdateRequest();
        nullLateAllowance.setAllowLateSubmission(null);
        assertBadRequest(() -> service.update(1L, nullLateAllowance, 7L));
    }

    @Test
    void missingMemberReturnsNotFoundBeforeRepositoryAccess() {
        when(memberRepository.findById(404L)).thenReturn(Optional.empty());

        assertNotFound(() -> service.getAssignments(404L, 0, 20, "createdAt,desc"));
        assertNotFound(() -> service.getAssignment(1L, 404L));
        assertNotFound(() -> service.create(mock(AssignmentCreateRequest.class), 404L));
        verify(assignmentRepository, never()).findById(any());
    }

    @Test
    void missingAssignmentReturnsNotFound() {
        Member admin = member(MemberRole.ADMIN);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(assignmentRepository.findById(99L)).thenReturn(Optional.empty());
        when(assignmentRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        AssignmentUpdateRequest update = new AssignmentUpdateRequest();
        update.setTitle("제목");

        assertNotFound(() -> service.getAssignment(99L, 7L));
        assertNotFound(() -> service.update(99L, update, 7L));
        assertNotFound(() -> service.delete(99L, 7L));
    }

    @Test
    void adminDeletesAssignmentWithoutSubmission() {
        Member admin = member(MemberRole.ADMIN);
        Assignment assignment = Assignment.create(admin, "제목", "내용", OffsetDateTime.now(), false);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(assignmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(assignment));
        when(submissionRepository.existsByAssignmentId(1L)).thenReturn(false);

        service.delete(1L, 7L);

        verify(assignmentRepository).delete(assignment);
    }

    @Test
    void assignmentWithSubmissionCannotBeDeleted() {
        Member admin = member(MemberRole.ADMIN);
        Assignment assignment = Assignment.create(admin, "제목", "내용", OffsetDateTime.now(), false);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(assignmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(assignment));
        when(submissionRepository.existsByAssignmentId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L, 7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(assignmentRepository, never()).delete(any());
    }

    private Member member(MemberRole role) {
        Member member = mock(Member.class);
        when(member.getRole()).thenReturn(role);
        return member;
    }

    @Test
    void futureDueAtUpdateUsesAsiaSeoulAndFlushes() {
        Member admin = member(MemberRole.ADMIN);
        Assignment assignment = Assignment.create(admin, "제목", "내용",
                OffsetDateTime.parse("2026-08-28T00:00:00+09:00"), true);
        AssignmentUpdateRequest request = new AssignmentUpdateRequest();
        request.setDueAt(LocalDateTime.parse("2026-08-29T23:59:59"));
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));

        service.update(1L, request, 7L);

        assertThat(assignment.getDueAt()).isEqualTo(OffsetDateTime.parse("2026-08-29T23:59:59+09:00"));
        assertThat(assignment.getDueAt().toInstant()).isEqualTo(Instant.parse("2026-08-29T14:59:59Z"));
        verify(assignmentRepository).flush();
    }

    @Test
    void currentOrPastDueAtUpdateDoesNotMutateAssignmentOrFlush() {
        assertInvalidUpdateDueAt(LocalDateTime.parse("2026-08-27T21:00:00"));
        assertInvalidUpdateDueAt(LocalDateTime.parse("2026-08-27T20:59:59"));
    }

    private AssignmentCreateRequest createRequest(LocalDateTime dueAt) {
        AssignmentCreateRequest request = mock(AssignmentCreateRequest.class);
        when(request.getTitle()).thenReturn("과제");
        when(request.getContent()).thenReturn("내용");
        when(request.getDueAt()).thenReturn(dueAt);
        when(request.getAllowLateSubmission()).thenReturn(false);
        return request;
    }

    private void assertInvalidCreateDueAt(LocalDateTime dueAt) {
        Member admin = member(MemberRole.ADMIN);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.create(createRequest(dueAt), 7L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo("마감 시각은 현재보다 미래여야 합니다.");
                });
        verify(assignmentRepository, never()).save(any());
    }

    private void assertInvalidUpdateDueAt(LocalDateTime dueAt) {
        Member admin = member(MemberRole.ADMIN);
        OffsetDateTime originalDueAt = OffsetDateTime.parse("2026-08-28T00:00:00+09:00");
        Assignment assignment = Assignment.create(admin, "기존 제목", "기존 내용", originalDueAt, true);
        AssignmentUpdateRequest request = new AssignmentUpdateRequest();
        request.setTitle("변경 제목");
        request.setDueAt(dueAt);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(assignmentRepository.findById(1L)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.update(1L, request, 7L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getMessage()).isEqualTo("마감 시각은 현재보다 미래여야 합니다.");
                });
        assertThat(assignment.getTitle()).isEqualTo("기존 제목");
        assertThat(assignment.getDueAt()).isEqualTo(originalDueAt);
        verify(assignmentRepository, never()).save(any());
        verify(assignmentRepository, never()).flush();
    }

    private void assertBadRequest(Runnable action) { assertStatus(action, HttpStatus.BAD_REQUEST); }
    private void assertForbidden(Runnable action) { assertStatus(action, HttpStatus.FORBIDDEN); }
    private void assertNotFound(Runnable action) { assertStatus(action, HttpStatus.NOT_FOUND); }
    private void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(status));
    }
}
