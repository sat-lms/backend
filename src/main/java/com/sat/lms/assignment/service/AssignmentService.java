package com.sat.lms.assignment.service;

import com.sat.lms.assignment.dto.AssignmentCreateRequest;
import com.sat.lms.assignment.dto.AssignmentDetailResponse;
import com.sat.lms.assignment.dto.AssignmentListResponse;
import com.sat.lms.assignment.dto.AssignmentUpdateRequest;
import com.sat.lms.assignment.entity.Assignment;
import com.sat.lms.assignment.repository.AssignmentRepository;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.repository.MemberRepository;
import com.sat.lms.submission.repository.SubmissionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
@Transactional(readOnly = true)
public class AssignmentService {
    private static final ZoneId ASSIGNMENT_TIME_ZONE = ZoneId.of("Asia/Seoul");
    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("createdAt", "updatedAt", "dueAt", "title");

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    public AssignmentService(AssignmentRepository assignmentRepository,
                             SubmissionRepository submissionRepository,
                             MemberRepository memberRepository,
                             Clock clock) {
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    @Transactional
    public AssignmentDetailResponse create(AssignmentCreateRequest request, Long memberId) {
        Member admin = requireAdmin(memberId);
        OffsetDateTime dueAt = validateAndConvertDueAt(request.getDueAt(), "마감 시각은 필수입니다.");
        Assignment assignment = Assignment.create(admin, request.getTitle().trim(), request.getContent().trim(),
                dueAt, request.getAllowLateSubmission());
        return AssignmentDetailResponse.from(assignmentRepository.save(assignment));
    }

    public Page<AssignmentListResponse> getAssignments(Long memberId, int page, int size, String sort) {
        requireMember(memberId);
        return assignmentRepository.findAssignmentPage(PageRequest.of(page, size, parseSort(sort)));
    }

    public AssignmentDetailResponse getAssignment(Long assignmentId, Long memberId) {
        requireMember(memberId);
        return AssignmentDetailResponse.from(findAssignment(assignmentId));
    }

    @Transactional
    public AssignmentDetailResponse update(Long assignmentId, AssignmentUpdateRequest request, Long memberId) {
        requireAdmin(memberId);
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

    @Transactional
    public void delete(Long assignmentId, Long memberId) {
        requireAdmin(memberId);
        Assignment assignment = assignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 과제입니다."));
        if (submissionRepository.existsByAssignmentId(assignmentId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "제출물이 존재하는 과제는 삭제할 수 없습니다.");
        }
        assignmentRepository.delete(assignment);
    }

    private Sort parseSort(String value) {
        if (value == null || value.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "dueAt").and(Sort.by(Sort.Direction.ASC, "id"));
        }
        String[] parts = value.split(",", -1);
        if (parts.length > 2 || parts[0].isBlank() || !ALLOWED_SORT_FIELDS.contains(parts[0])) {
            throw invalidSort();
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length == 2) {
            try {
                direction = Sort.Direction.fromString(parts[1]);
            } catch (IllegalArgumentException e) {
                throw invalidSort();
            }
        }
        return Sort.by(direction, parts[0]).and(Sort.by(direction, "id"));
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

    private Member requireMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."));
    }

    private Member requireAdmin(Long memberId) {
        Member member = requireMember(memberId);
        if (member.getRole() != MemberRole.ADMIN) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
        return member;
    }

    private BusinessException invalidSort() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "허용되지 않는 정렬 조건입니다.");
    }
}
