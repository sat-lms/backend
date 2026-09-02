package com.sat.lms.submission.service;

import com.sat.lms.assignment.entity.Assignment;
import com.sat.lms.assignment.repository.AssignmentRepository;
import com.sat.lms.attachment.entity.Attachment;
import com.sat.lms.attachment.entity.SubmissionAttachment;
import com.sat.lms.attachment.repository.AttachmentRepository;
import com.sat.lms.attachment.repository.SubmissionAttachmentRepository;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.storage.DownloadUrl;
import com.sat.lms.global.storage.FileStorage;
import com.sat.lms.global.storage.FileExtensionExtractor;
import com.sat.lms.global.storage.StoredFile;
import com.sat.lms.attachment.service.AttachmentStorageLifecycle;
import com.sat.lms.global.transaction.ShortTransactionExecutor;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.service.MemberGuard;
import com.sat.lms.submission.dto.SubmissionAttachmentDownloadUrlResponse;
import com.sat.lms.submission.dto.SubmissionCreateRequest;
import com.sat.lms.submission.dto.SubmissionDetailResponse;
import com.sat.lms.submission.dto.SubmissionFileResponse;
import com.sat.lms.submission.dto.SubmissionListResponse;
import com.sat.lms.submission.dto.MySubmissionSort;
import com.sat.lms.submission.entity.Submission;
import com.sat.lms.submission.repository.SubmissionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SubmissionService {
    private static final int MAX_FILE_COUNT = 5;
    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;
    private static final long MAX_TOTAL_SIZE_BYTES = 100L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "java", "py", "c", "cpp", "h", "hpp", "cs", "js", "ts", "jsx", "tsx", "go", "rb", "php", "kt", "swift",
            "txt", "md", "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "zip",
            "png", "jpg", "jpeg", "gif", "json", "yml", "yaml", "xml", "html", "css", "sql", "sh");
    private static final String EMPTY_CONTENT_MESSAGE = "textContent와 files 중 하나 이상은 필수입니다.";
    private static final String NOT_FOUND_SUBMISSION_MESSAGE = "제출물이 존재하지 않습니다.";
    private static final String NOT_FOUND_ATTACHMENT_MESSAGE = "존재하지 않는 제출 파일입니다.";
    private static final String FORBIDDEN_OWNER_MESSAGE = "본인 제출물만 접근할 수 있습니다.";
    private static final String LATE_BLOCKED_MESSAGE = "마감된 과제이며 지각 제출이 허용되지 않습니다.";

    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final MemberGuard memberGuard;
    private final AttachmentRepository attachmentRepository;
    private final SubmissionAttachmentRepository submissionAttachmentRepository;
    private final FileStorage fileStorage;
    private final Clock clock;
    private final AttachmentStorageLifecycle storageLifecycle;
    private final ShortTransactionExecutor transactions;
    private final SubmissionStatusCalculator submissionStatusCalculator;

    public SubmissionService(SubmissionRepository submissionRepository, AssignmentRepository assignmentRepository,
                             MemberGuard memberGuard, AttachmentRepository attachmentRepository,
                             SubmissionAttachmentRepository submissionAttachmentRepository, FileStorage fileStorage,
                             Clock clock, AttachmentStorageLifecycle storageLifecycle,
                             ShortTransactionExecutor transactions,
                             SubmissionStatusCalculator submissionStatusCalculator) {
        this.submissionRepository = submissionRepository;
        this.assignmentRepository = assignmentRepository;
        this.memberGuard = memberGuard;
        this.attachmentRepository = attachmentRepository;
        this.submissionAttachmentRepository = submissionAttachmentRepository;
        this.fileStorage = fileStorage;
        this.clock = clock;
        this.storageLifecycle = storageLifecycle;
        this.transactions = transactions;
        this.submissionStatusCalculator = submissionStatusCalculator;
    }

    @Transactional(readOnly = true)
    public SubmissionDetailResponse getMySubmission(Long assignmentId, Long memberId) {
        memberGuard.requireStudent(memberId);
        Submission submission = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_SUBMISSION_MESSAGE));
        List<Attachment> attachments = submissionAttachmentRepository
                .findWithAttachmentBySubmissionId(submission.getId()).stream()
                .map(SubmissionAttachment::getAttachment)
                .toList();
        return SubmissionDetailResponse.from(submission, attachments);
    }

    public SubmissionDetailResponse submit(Long assignmentId, Long memberId, SubmissionCreateRequest request,
                                           List<MultipartFile> files) {
        transactions.requireNonTransactionalEntry();
        transactions.read(() -> memberGuard.requireStudent(memberId));
        String textContent = normalizeText(request);
        boolean hasText = hasText(textContent);
        List<MultipartFile> submittedFiles = files == null ? List.of() : files;
        boolean hasFiles = !submittedFiles.isEmpty();
        if (!hasText && !hasFiles) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, EMPTY_CONTENT_MESSAGE);
        }
        validateFiles(submittedFiles);

        DeadlineSnapshot deadline = transactions.read(() -> snapshotDeadline(requireAssignment(assignmentId)));

        List<StoredFile> uploaded = new ArrayList<>();
        try {
            String directory = "submissions/" + UUID.randomUUID();
            for (MultipartFile file : submittedFiles) {
                uploaded.add(fileStorage.upload(file, directory));
            }
        } catch (RuntimeException e) {
            compensate(uploaded);
            throw e;
        }
        try {
            return transactions.writeWithRollbackConfirmation(() -> saveNewSubmission(assignmentId, memberId,
                    hasText ? textContent : null, deadline.late(), uploaded));
        } catch (ShortTransactionExecutor.ConfirmedRollbackException e) {
            compensate(uploaded);
            throw e.originalException();
        }
    }

    public SubmissionDetailResponse resubmit(Long assignmentId, Long memberId, SubmissionCreateRequest request,
                                             List<MultipartFile> files) {
        transactions.requireNonTransactionalEntry();
        transactions.read(() -> memberGuard.requireStudent(memberId));
        String textContent = normalizeText(request);
        boolean hasText = hasText(textContent);
        List<MultipartFile> submittedFiles = files == null ? List.of() : files;
        boolean hasFiles = !submittedFiles.isEmpty();
        if (!hasText && !hasFiles) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, EMPTY_CONTENT_MESSAGE);
        }
        validateFiles(submittedFiles);

        DeadlineSnapshot deadline = transactions.read(() -> {
            Assignment assignment = requireAssignment(assignmentId);
            submissionRepository.findByAssignmentIdAndStudentId(assignmentId, memberId)
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_SUBMISSION_MESSAGE));
            return snapshotDeadline(assignment);
        });

        List<StoredFile> uploaded = new ArrayList<>();
        try {
            String directory = "submissions/" + UUID.randomUUID();
            for (MultipartFile file : submittedFiles) {
                uploaded.add(fileStorage.upload(file, directory));
            }
        } catch (RuntimeException e) {
            compensate(uploaded);
            throw e;
        }
        try {
            ResubmitResult result = transactions.writeWithRollbackConfirmation(() -> replaceSubmission(assignmentId,
                    memberId, hasText ? textContent : null, deadline.late(), uploaded));
            storageLifecycle.delete(result.oldStorageKeys());
            return result.response();
        } catch (ShortTransactionExecutor.ConfirmedRollbackException e) {
            compensate(uploaded);
            throw e.originalException();
        }
    }

    public void deleteSubmission(Long assignmentId, Long memberId) {
        transactions.requireNonTransactionalEntry();
        transactions.read(() -> memberGuard.requireStudent(memberId));
        List<String> storageKeys = transactions.write(() -> deleteSubmissionInTransaction(assignmentId, memberId));
        storageLifecycle.delete(storageKeys);
    }

    private List<String> deleteSubmissionInTransaction(Long assignmentId, Long memberId) {
        memberGuard.requireStudent(memberId);
        assignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 과제입니다."));
        Submission submission = submissionRepository.findByAssignmentIdAndStudentIdForUpdate(assignmentId, memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_SUBMISSION_MESSAGE));

        List<SubmissionAttachment> links = submissionAttachmentRepository
                .findWithAttachmentBySubmissionId(submission.getId());
        List<Attachment> attachments = links.stream().map(SubmissionAttachment::getAttachment).toList();
        List<String> storageKeys = attachments.stream().map(Attachment::getStorageKey).toList();

        submissionAttachmentRepository.deleteAll(links);
        submissionAttachmentRepository.flush();
        attachmentRepository.deleteAll(attachments);
        attachmentRepository.flush();
        submissionRepository.delete(submission);
        submissionRepository.flush();

        return storageKeys;
    }

    @Transactional(readOnly = true)
    public Page<SubmissionListResponse> getMySubmissions(Long memberId, boolean includeNotSubmitted,
                                                         MySubmissionSort sort, Pageable pageable) {
        memberGuard.requireStudent(memberId);
        var now = clock.instant();
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<SubmissionListResponse> page = switch (sort) {
            case DUE_AT_DESC -> submissionRepository.findMySubmissionPageDueAtDesc(
                    memberId, includeNotSubmitted, unsorted);
            case DUE_AT_ASC -> submissionRepository.findMySubmissionPageDueAtAsc(
                    memberId, includeNotSubmitted, unsorted);
            case SUBMITTED_AT_DESC -> submissionRepository.findMySubmissionPageSubmittedAtDesc(
                    memberId, includeNotSubmitted, unsorted);
        };
        if (page.isEmpty()) {
            return page;
        }
        page.forEach(item -> item.assignSubmissionStatus(submissionStatusCalculator.calculate(
                item.getSubmissionId(), item.getIsLate(), item.getDueAt(),
                item.isAllowLateSubmission(), now)));
        List<Long> submissionIds = page.getContent().stream()
                .map(SubmissionListResponse::getSubmissionId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<SubmissionFileResponse>> attachmentsBySubmissionId = submissionIds.isEmpty()
                ? Map.of()
                : submissionAttachmentRepository.findWithAttachmentBySubmissionIdIn(submissionIds).stream()
                        .collect(Collectors.groupingBy(
                                link -> link.getSubmission().getId(),
                                Collectors.mapping(link -> SubmissionFileResponse.from(link.getAttachment()),
                                        Collectors.toList())));
        page.getContent().forEach(item -> item.assignAttachments(item.getSubmissionId() == null
                ? List.of()
                : attachmentsBySubmissionId.getOrDefault(item.getSubmissionId(), List.of())));
        return page;
    }


    @Transactional(readOnly = true)
    public SubmissionAttachmentDownloadUrlResponse getDownloadUrl(Long attachmentId, Long memberId) {
        Member requester = memberGuard.requireMember(memberId);
        SubmissionAttachment link = submissionAttachmentRepository
                .findWithSubmissionAndAttachmentByAttachmentId(attachmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_ATTACHMENT_MESSAGE));
        requireOwnerOrAdmin(requester, link.getSubmission().getStudent());

        Attachment attachment = link.getAttachment();
        DownloadUrl downloadUrl = fileStorage.createDownloadUrl(attachment.getStorageKey());
        return new SubmissionAttachmentDownloadUrlResponse(
                downloadUrl.url(), downloadUrl.expiresInSeconds(), attachment.getOriginalName());
    }

    public void deleteAttachment(Long attachmentId, Long memberId) {
        transactions.requireNonTransactionalEntry();
        transactions.read(() -> memberGuard.requireStudent(memberId));
        AttachmentTarget target = transactions.read(() -> requireAttachmentTarget(attachmentId, memberId));
        List<String> storageKeys = transactions.write(() ->
                deleteAttachmentInTransaction(target.assignmentId(), attachmentId, memberId));
        storageLifecycle.delete(storageKeys);
    }

    private List<String> deleteAttachmentInTransaction(Long assignmentId, Long attachmentId, Long memberId) {
        Member requester = memberGuard.requireStudent(memberId);
        assignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 과제입니다."));
        Submission lockedSubmission = submissionRepository
                .findByAssignmentIdAndStudentIdForUpdate(assignmentId, memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_SUBMISSION_MESSAGE));
        SubmissionAttachment link = submissionAttachmentRepository
                .findWithSubmissionAndAttachmentByAttachmentId(attachmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_ATTACHMENT_MESSAGE));
        Submission submission = link.getSubmission();
        if (!submission.getId().equals(lockedSubmission.getId())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_ATTACHMENT_MESSAGE);
        }
        if (!submission.getStudent().getId().equals(requester.getId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, FORBIDDEN_OWNER_MESSAGE);
        }
        determineLateAndRequireEditable(submission.getAssignment());

        long remainingFileCount = submissionAttachmentRepository.countBySubmissionId(submission.getId()) - 1;
        boolean textRemains = submission.getTextContent() != null && !submission.getTextContent().isBlank();
        if (remainingFileCount <= 0 && !textRemains) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "삭제 후 제출물이 완전히 비게 됩니다. 제출물 전체를 삭제하려면 DELETE .../submission을 사용하세요.");
        }

        Attachment attachment = link.getAttachment();
        submissionAttachmentRepository.delete(link);
        submissionAttachmentRepository.flush();
        attachmentRepository.delete(attachment);
        attachmentRepository.flush();

        return List.of(attachment.getStorageKey());
    }

    private void compensate(List<StoredFile> uploaded) {
        storageLifecycle.delete(uploaded.stream().map(StoredFile::storageKey).toList());
    }

    private SubmissionDetailResponse saveNewSubmission(Long assignmentId, Long memberId, String textContent,
                                                       boolean late, List<StoredFile> uploaded) {
        Member student = memberGuard.requireStudent(memberId);
        Assignment assignment = assignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 과제입니다."));
        Submission submission = submissionRepository.save(Submission.create(assignment, student, textContent, late));
        List<Attachment> attachments = persistAttachments(submission, uploaded);
        return SubmissionDetailResponse.from(submission, attachments);
    }

    private ResubmitResult replaceSubmission(Long assignmentId, Long memberId, String textContent,
                                              boolean late, List<StoredFile> uploaded) {
        memberGuard.requireStudent(memberId);
        assignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 과제입니다."));
        Submission submission = submissionRepository.findByAssignmentIdAndStudentIdForUpdate(assignmentId, memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_SUBMISSION_MESSAGE));
        List<SubmissionAttachment> oldLinks = submissionAttachmentRepository
                .findWithAttachmentBySubmissionId(submission.getId());
        List<Attachment> oldAttachments = oldLinks.stream().map(SubmissionAttachment::getAttachment).toList();
        List<Attachment> newAttachments = persistAttachments(submission, uploaded);
        submissionAttachmentRepository.deleteAll(oldLinks);
        submissionAttachmentRepository.flush();
        attachmentRepository.deleteAll(oldAttachments);
        attachmentRepository.flush();
        submission.resubmit(textContent, late);
        return new ResubmitResult(SubmissionDetailResponse.from(submission, newAttachments),
                oldAttachments.stream().map(Attachment::getStorageKey).toList());
    }

    private List<Attachment> persistAttachments(Submission submission, List<StoredFile> uploaded) {
        List<Attachment> attachments = uploaded.stream()
                .map(stored -> Attachment.create(stored.originalName(), stored.storedName(),
                        stored.storageKey(), stored.extension(), stored.sizeKb()))
                .toList();
        attachmentRepository.saveAll(attachments);
        attachmentRepository.flush();
        submissionAttachmentRepository.saveAll(attachments.stream()
                .map(attachment -> SubmissionAttachment.create(submission, attachment)).toList());
        submissionAttachmentRepository.flush();
        return attachments;
    }

    private record ResubmitResult(SubmissionDetailResponse response, List<String> oldStorageKeys) {}
    private record DeadlineSnapshot(java.time.Instant checkedAt, java.time.Instant dueAt,
                                    boolean allowLateSubmission, boolean late) {}
    private record AttachmentTarget(Long assignmentId) {}

    private AttachmentTarget requireAttachmentTarget(Long attachmentId, Long memberId) {
        SubmissionAttachment link = submissionAttachmentRepository
                .findWithSubmissionAndAttachmentByAttachmentId(attachmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_ATTACHMENT_MESSAGE));
        if (!link.getSubmission().getStudent().getId().equals(memberId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, FORBIDDEN_OWNER_MESSAGE);
        }
        return new AttachmentTarget(link.getSubmission().getAssignment().getId());
    }

    private DeadlineSnapshot snapshotDeadline(Assignment assignment) {
        java.time.Instant checkedAt = clock.instant();
        java.time.Instant dueAt = assignment.getDueAt().toInstant();
        boolean allowLate = assignment.isAllowLateSubmission();
        boolean late = checkedAt.isAfter(dueAt);
        if (late && !allowLate) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, LATE_BLOCKED_MESSAGE);
        }
        return new DeadlineSnapshot(checkedAt, dueAt, allowLate, late);
    }

    private String normalizeText(SubmissionCreateRequest request) {
        return request.getTextContent() == null ? null : request.getTextContent().trim();
    }

    private boolean hasText(String textContent) {
        return textContent != null && !textContent.isBlank();
    }

    private boolean determineLateAndRequireEditable(Assignment assignment) {
        boolean late = clock.instant().isAfter(assignment.getDueAt().toInstant());
        if (late && !assignment.isAllowLateSubmission()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, LATE_BLOCKED_MESSAGE);
        }
        return late;
    }

    private void validateFiles(List<MultipartFile> files) {
        if (files.size() > MAX_FILE_COUNT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "파일은 최대 " + MAX_FILE_COUNT + "개까지 첨부할 수 있습니다.");
        }
        long total = 0;
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "빈 파일은 업로드할 수 없습니다.");
            }
            if (file.getSize() > MAX_FILE_SIZE_BYTES) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "파일 1개의 용량은 50MB를 초과할 수 없습니다.");
            }
            if (!ALLOWED_EXTENSIONS.contains(FileExtensionExtractor.extract(file.getOriginalFilename()))) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "허용되지 않는 파일 확장자입니다.");
            }
            total += file.getSize();
        }
        if (total > MAX_TOTAL_SIZE_BYTES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "전체 파일 용량은 100MB를 초과할 수 없습니다.");
        }
    }

    private Assignment requireAssignment(Long assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 과제입니다."));
    }

    private void requireOwnerOrAdmin(Member requester, Member owner) {
        boolean isOwner = requester.getId().equals(owner.getId());
        boolean isAdmin = requester.getRole() == MemberRole.ADMIN;
        if (!isOwner && !isAdmin) {
            throw new BusinessException(HttpStatus.FORBIDDEN, FORBIDDEN_OWNER_MESSAGE);
        }
    }
}
