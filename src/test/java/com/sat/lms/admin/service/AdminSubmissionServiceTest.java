package com.sat.lms.admin.service;

import com.sat.lms.assignment.entity.Assignment;
import com.sat.lms.assignment.repository.AssignmentRepository;
import com.sat.lms.attachment.entity.Attachment;
import com.sat.lms.attachment.entity.SubmissionAttachment;
import com.sat.lms.attachment.repository.SubmissionAttachmentRepository;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.repository.MemberRepository;
import com.sat.lms.submission.dto.AdminAssignmentSubmissionCounts;
import com.sat.lms.submission.dto.AdminSubmissionStudentRow;
import com.sat.lms.submission.dto.SubmissionStatusFilter;
import com.sat.lms.submission.entity.Submission;
import com.sat.lms.submission.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminSubmissionServiceTest {
    MemberRepository memberRepository;
    AssignmentRepository assignmentRepository;
    SubmissionRepository submissionRepository;
    SubmissionAttachmentRepository submissionAttachmentRepository;
    AdminSubmissionService service;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        assignmentRepository = mock(AssignmentRepository.class);
        submissionRepository = mock(SubmissionRepository.class);
        submissionAttachmentRepository = mock(SubmissionAttachmentRepository.class);
        service = new AdminSubmissionService(memberRepository, assignmentRepository, submissionRepository,
                submissionAttachmentRepository);
    }

    @Test
    void getSubmissionStatusReturnsCountsAndStudentPage() {
        Member admin = admin(7L);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(assignmentRepository.existsById(1L)).thenReturn(true);
        AdminAssignmentSubmissionCounts counts = new AdminAssignmentSubmissionCounts(2L, 2L, 1L);
        when(memberRepository.countSubmissionStatusByAssignmentId(1L)).thenReturn(counts);
        Pageable pageable = PageRequest.of(0, 20);
        AdminSubmissionStudentRow row = new AdminSubmissionStudentRow(10L, "20231234", "학생",
                OffsetDateTime.now(), true);
        Page<AdminSubmissionStudentRow> page = new PageImpl<>(List.of(row), pageable, 1);
        when(memberRepository.findStudentSubmissionStatusPage(1L, null, pageable)).thenReturn(page);

        var response = service.getSubmissionStatus(1L, null, pageable, 7L);

        assertThat(response.getSubmittedCount()).isEqualTo(2L);
        assertThat(response.getNotSubmittedCount()).isEqualTo(2L);
        assertThat(response.getLateCount()).isEqualTo(1L);
        assertThat(response.getStudents().getContent()).hasSize(1);
        assertThat(response.getStudents().getContent().get(0).getStudentNumber()).isEqualTo("20231234");
    }

    @Test
    void getSubmissionStatusPassesStatusFilterThrough() {
        Member admin = admin(7L);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(assignmentRepository.existsById(1L)).thenReturn(true);
        when(memberRepository.countSubmissionStatusByAssignmentId(1L))
                .thenReturn(new AdminAssignmentSubmissionCounts(0L, 0L, 0L));
        Pageable pageable = PageRequest.of(0, 20);
        when(memberRepository.findStudentSubmissionStatusPage(1L, "LATE", pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.getSubmissionStatus(1L, SubmissionStatusFilter.LATE, pageable, 7L);

        verify(memberRepository).findStudentSubmissionStatusPage(1L, "LATE", pageable);
    }

    @Test
    void getSubmissionStatusForbiddenForStudent() {
        Member student = mock(Member.class);
        when(student.getRole()).thenReturn(MemberRole.STUDENT);
        when(memberRepository.findById(3L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> service.getSubmissionStatus(1L, null, PageRequest.of(0, 20), 3L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(assignmentRepository, never()).existsById(any());
    }

    @Test
    void getSubmissionStatusMissingAssignmentReturnsNotFound() {
        Member admin = admin(7L);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(assignmentRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.getSubmissionStatus(99L, null, PageRequest.of(0, 20), 7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(memberRepository, never()).countSubmissionStatusByAssignmentId(any());
        verify(memberRepository, never()).findStudentSubmissionStatusPage(any(), any(), any());
    }

    @Test
    void getSubmissionDetailReturnsMappedResponse() {
        Member admin = admin(7L);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        Member student = mock(Member.class);
        when(student.getStudentNumber()).thenReturn("20231234");
        when(student.getName()).thenReturn("학생");
        Assignment assignment = mock(Assignment.class);
        when(assignment.getId()).thenReturn(1L);
        when(assignment.getTitle()).thenReturn("과제 제목");
        Submission submission = Submission.create(assignment, student, "제출 내용", true);
        when(submissionRepository.findWithStudentAndAssignmentById(5L)).thenReturn(Optional.of(submission));
        Attachment attachment = Attachment.create("a.txt", "stored.txt", "submissions/5/stored.txt", "txt", 1L);
        SubmissionAttachment link = SubmissionAttachment.create(submission, attachment);
        when(submissionAttachmentRepository.findWithAttachmentBySubmissionId(5L)).thenReturn(List.of(link));

        var response = service.getSubmissionDetail(5L, 7L);

        assertThat(response.getAssignmentId()).isEqualTo(1L);
        assertThat(response.getAssignmentTitle()).isEqualTo("과제 제목");
        assertThat(response.getStudentNumber()).isEqualTo("20231234");
        assertThat(response.getStudentName()).isEqualTo("학생");
        assertThat(response.getTextContent()).isEqualTo("제출 내용");
        assertThat(response.getIsLate()).isTrue();
        assertThat(response.getFiles()).hasSize(1);
        assertThat(response.getFiles().get(0).getOriginalName()).isEqualTo("a.txt");
    }

    @Test
    void getSubmissionDetailMissingReturnsNotFound() {
        Member admin = admin(7L);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(submissionRepository.findWithStudentAndAssignmentById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSubmissionDetail(99L, 7L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getSubmissionDetailForbiddenForStudent() {
        Member student = mock(Member.class);
        when(student.getRole()).thenReturn(MemberRole.STUDENT);
        when(memberRepository.findById(3L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> service.getSubmissionDetail(5L, 3L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(submissionRepository, never()).findWithStudentAndAssignmentById(any());
    }

    @Test
    void getSubmissionDetailUnknownMemberReturnsNotFound() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSubmissionDetail(5L, 99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private Member admin(Long id) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(id);
        when(member.getRole()).thenReturn(MemberRole.ADMIN);
        return member;
    }
}