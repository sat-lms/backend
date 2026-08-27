package com.sat.lms.admin.service;

import com.sat.lms.assignment.repository.AssignmentRepository;
import com.sat.lms.attachment.entity.Attachment;
import com.sat.lms.attachment.entity.SubmissionAttachment;
import com.sat.lms.attachment.repository.SubmissionAttachmentRepository;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.response.PageResponse;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.repository.MemberRepository;
import com.sat.lms.submission.dto.AdminAssignmentSubmissionCounts;
import com.sat.lms.submission.dto.AdminSubmissionDetailResponse;
import com.sat.lms.submission.dto.AdminSubmissionStudentRow;
import com.sat.lms.submission.dto.AdminSubmissionSummaryResponse;
import com.sat.lms.submission.dto.SubmissionStatusFilter;
import com.sat.lms.submission.entity.Submission;
import com.sat.lms.submission.repository.SubmissionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdminSubmissionService {
    private final MemberRepository memberRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionAttachmentRepository submissionAttachmentRepository;

    public AdminSubmissionService(MemberRepository memberRepository, AssignmentRepository assignmentRepository,
                                  SubmissionRepository submissionRepository,
                                  SubmissionAttachmentRepository submissionAttachmentRepository) {
        this.memberRepository = memberRepository;
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.submissionAttachmentRepository = submissionAttachmentRepository;
    }

    public AdminSubmissionSummaryResponse getSubmissionStatus(Long assignmentId, SubmissionStatusFilter status,
                                                              Pageable pageable, Long memberId) {
        requireAdmin(memberId);
        requireAssignment(assignmentId);

        AdminAssignmentSubmissionCounts counts = memberRepository.countSubmissionStatusByAssignmentId(assignmentId);
        String statusName = status == null ? null : status.name();
        Page<AdminSubmissionStudentRow> students = memberRepository
                .findStudentSubmissionStatusPage(assignmentId, statusName, pageable);

        return new AdminSubmissionSummaryResponse(counts.getSubmittedCount(), counts.getNotSubmittedCount(),
                counts.getLateCount(), PageResponse.from(students));
    }

    public AdminSubmissionDetailResponse getSubmissionDetail(Long submissionId, Long memberId) {
        requireAdmin(memberId);
        Submission submission = submissionRepository.findWithStudentAndAssignmentById(submissionId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 제출물입니다."));
        List<Attachment> attachments = submissionAttachmentRepository
                .findWithAttachmentBySubmissionId(submissionId).stream()
                .map(SubmissionAttachment::getAttachment)
                .toList();
        return AdminSubmissionDetailResponse.from(submission, attachments);
    }

    private void requireAssignment(Long assignmentId) {
        if (!assignmentRepository.existsById(assignmentId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 과제입니다.");
        }
    }

    private Member requireAdmin(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."));
        if (member.getRole() != MemberRole.ADMIN) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
        return member;
    }
}
