package com.sat.lms.assignment.service;

import com.sat.lms.assignment.dto.AssignmentCreateRequest;
import com.sat.lms.assignment.dto.AssignmentDetailResponse;
import com.sat.lms.assignment.dto.AssignmentListResponse;
import com.sat.lms.assignment.dto.AssignmentUpdateRequest;
import com.sat.lms.assignment.entity.Assignment;
import com.sat.lms.assignment.repository.AssignmentRepository;
import com.sat.lms.attachment.repository.AssignmentAttachmentRepository;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.service.MemberGuard;
import com.sat.lms.attachment.service.AttachmentStorageLifecycle;
import com.sat.lms.global.transaction.ShortTransactionExecutor;
import com.sat.lms.submission.repository.SubmissionRepository;
import com.sat.lms.submission.service.SubmissionStatusCalculator;
import com.sat.lms.member.entity.MemberRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Set;

@Service
public class AssignmentService {
    private static final ZoneId ASSIGNMENT_TIME_ZONE = ZoneId.of("Asia/Seoul");
    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("createdAt", "updatedAt", "dueAt", "title");

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final MemberGuard memberGuard;
    private final Clock clock;
    private final AssignmentAttachmentRepository assignmentAttachmentRepository;
    private final AssignmentAttachmentCleanup attachmentCleanup;
    private final AttachmentStorageLifecycle storageLifecycle;
    private final ShortTransactionExecutor transactions;
    private final SubmissionStatusCalculator submissionStatusCalculator;

    public AssignmentService(AssignmentRepository assignmentRepository,
                             SubmissionRepository submissionRepository,
                             MemberGuard memberGuard,
                             Clock clock,
                             AssignmentAttachmentRepository assignmentAttachmentRepository,
                             AssignmentAttachmentCleanup attachmentCleanup,
                             AttachmentStorageLifecycle storageLifecycle,
                             ShortTransactionExecutor transactions,
                             SubmissionStatusCalculator submissionStatusCalculator) {
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.memberGuard = memberGuard;
        this.clock = clock;
        this.assignmentAttachmentRepository = assignmentAttachmentRepository;
        this.attachmentCleanup = attachmentCleanup;
        this.storageLifecycle = storageLifecycle;
        this.transactions = transactions;
        this.submissionStatusCalculator = submissionStatusCalculator;
    }

    @Transactional
    public AssignmentDetailResponse create(AssignmentCreateRequest request, Long memberId) {
        Member admin = memberGuard.requireAdmin(memberId);
        OffsetDateTime dueAt = validateAndConvertDueAt(request.getDueAt(), "마감 시각은 필수입니다.");
        Assignment assignment = Assignment.create(admin, request.getTitle().trim(), request.getContent().trim(),
                dueAt, request.getAllowLateSubmission());
        return AssignmentDetailResponse.from(assignmentRepository.save(assignment));
    }

    @Transactional(readOnly = true)
    public Page<AssignmentListResponse> getAssignments(Long memberId, Pageable pageable) {
        Member requester = memberGuard.requireMember(memberId);
        Pageable validated = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                validateSort(pageable.getSort()));
        if (requester.getRole() != MemberRole.STUDENT) {
            return assignmentRepository.findAssignmentPage(validated);
        }
        var now = clock.instant();
        Page<AssignmentListResponse> page = assignmentRepository.findStudentAssignmentPage(memberId, validated);
        page.forEach(item -> item.assignSubmissionStatus(submissionStatusCalculator.calculate(
                item.getSubmissionIdForStatus(), item.isSubmissionLateForStatus(), item.getDueAt(),
                item.getAllowLateSubmission(), now)));
        return page;
    }

    @Transactional(readOnly = true)
    public AssignmentDetailResponse getAssignment(Long assignmentId, Long memberId) {
        memberGuard.requireMember(memberId);
        Assignment assignment = findAssignment(assignmentId);
        return AssignmentDetailResponse.from(assignment,
                assignmentAttachmentRepository.findWithAttachmentByAssignmentId(assignmentId));
    }

    @Transactional
    public AssignmentDetailResponse update(Long assignmentId, AssignmentUpdateRequest request, Long memberId) {
        memberGuard.requireAdmin(memberId);
        validateUpdate(request);
        OffsetDateTime dueAt = request.isDueAtPresent()
                ? validateAndConvertDueAt(request.getDueAt(), "마감 시각은 null일 수 없습니다.")
                : null;
        Assignment assignment = findAssignment(assignmentId);
        assignment.update(
                request.isTitlePresent() ? request.getTitle().trim() : null,
                request.isContentPresent() ? request.getContent().trim() : null,
                dueAt,
                request.isAllowLateSubmissionPresent() ? request.getAllowLateSubmission() : null
        );
        assignmentRepository.flush();
        return AssignmentDetailResponse.from(assignment);
    }

    public void delete(Long assignmentId, Long memberId) {
        transactions.requireNonTransactionalEntry();
        transactions.read(() -> { memberGuard.requireAdmin(memberId); findAssignment(assignmentId); return null; });
        var keys = transactions.write(() -> {
            memberGuard.requireAdmin(memberId);
            Assignment assignment = assignmentRepository.findByIdForUpdate(assignmentId)
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 과제입니다."));
            if (submissionRepository.existsByAssignmentId(assignmentId))
                throw new BusinessException(HttpStatus.CONFLICT, "제출물이 존재하는 과제는 삭제할 수 없습니다.");
            var deletionKeys = attachmentCleanup.deleteAllForAssignment(assignmentId);
            assignmentRepository.delete(assignment);
            assignmentRepository.flush();
            return deletionKeys;
        });
        storageLifecycle.delete(keys);
    }

    private Sort validateSort(Sort requestedSort) {
        if (requestedSort.isUnsorted()) {
            return Sort.by(Sort.Direction.ASC, "dueAt").and(Sort.by(Sort.Direction.ASC, "id"));
        }
        if (requestedSort.stream().count() != 1) {
            throw invalidSort();
        }
        Sort.Order order = requestedSort.iterator().next();
        if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
            throw invalidSort();
        }
        return Sort.by(order).and(Sort.by(order.getDirection(), "id"));
    }

    private void validateUpdate(AssignmentUpdateRequest request) {
        if (request.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, "수정할 필드를 입력해주세요.");
        if (request.isTitlePresent() && (request.getTitle() == null || request.getTitle().isBlank())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "제목은 공백일 수 없습니다.");
        }
        if (request.isContentPresent() && (request.getContent() == null || request.getContent().isBlank())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "내용은 공백일 수 없습니다.");
        }
        if (request.isDueAtPresent() && request.getDueAt() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "마감 시각은 null일 수 없습니다.");
        }
        if (request.isAllowLateSubmissionPresent() && request.getAllowLateSubmission() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "지각 제출 허용 여부는 null일 수 없습니다.");
        }
    }

    private OffsetDateTime validateAndConvertDueAt(LocalDateTime dueAt, String nullMessage) {
        if (dueAt == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, nullMessage);
        }
        OffsetDateTime convertedDueAt = dueAt.atZone(ASSIGNMENT_TIME_ZONE).toOffsetDateTime();
        if (!convertedDueAt.toInstant().isAfter(clock.instant())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "마감 시각은 현재보다 미래여야 합니다.");
        }
        return convertedDueAt;
    }

    private Assignment findAssignment(Long assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 과제입니다."));
    }

    private BusinessException invalidSort() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "허용되지 않는 정렬 조건입니다.");
    }
}
