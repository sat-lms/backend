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
import com.sat.lms.submission.dto.SubmissionDetailResponse;
import com.sat.lms.submission.entity.Submission;
import com.sat.lms.submission.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class SubmissionService {
    private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);
    private static final int MAX_FILE_COUNT = 5;
    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;
    private static final long MAX_TOTAL_SIZE_BYTES = 100L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "java", "py", "c", "cpp", "h", "hpp", "cs", "js", "ts", "jsx", "tsx", "go", "rb", "php", "kt", "swift",
            "txt", "md", "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "zip",
            "png", "jpg", "jpeg", "gif", "json", "yml", "yaml", "xml", "html", "css", "sql", "sh");

    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final MemberRepository memberRepository;
    private final AttachmentRepository attachmentRepository;
    private final SubmissionAttachmentRepository submissionAttachmentRepository;
    private final FileStorage fileStorage;

    public SubmissionService(SubmissionRepository submissionRepository, AssignmentRepository assignmentRepository,
                             MemberRepository memberRepository, AttachmentRepository attachmentRepository,
                             SubmissionAttachmentRepository submissionAttachmentRepository, FileStorage fileStorage) {
        this.submissionRepository = submissionRepository;
        this.assignmentRepository = assignmentRepository;
        this.memberRepository = memberRepository;
        this.attachmentRepository = attachmentRepository;
        this.submissionAttachmentRepository = submissionAttachmentRepository;
        this.fileStorage = fileStorage;
    }

    public SubmissionDetailResponse getMySubmission(Long assignmentId, Long memberId) {
        requireStudent(memberId);
        Submission submission = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "제출물이 존재하지 않습니다."));
        List<Attachment> attachments = submissionAttachmentRepository
                .findWithAttachmentBySubmissionId(submission.getId()).stream()
                .map(SubmissionAttachment::getAttachment)
                .toList();
        return SubmissionDetailResponse.from(submission, attachments);
    }

    @Transactional
    public SubmissionDetailResponse submit(Long assignmentId, Long memberId, SubmissionCreateRequest request,
                                           List<MultipartFile> files) {
        Member student = requireStudent(memberId);
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 과제입니다."));

        String textContent = request.getTextContent() == null ? null : request.getTextContent().trim();
        boolean hasText = textContent != null && !textContent.isBlank();
        List<MultipartFile> submittedFiles = files == null ? List.of() : files;
        boolean hasFiles = !submittedFiles.isEmpty();
        if (!hasText && !hasFiles) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "textContent와 files 중 하나 이상은 필수입니다.");
        }
        validateFiles(submittedFiles);

        boolean late = OffsetDateTime.now(ZoneOffset.UTC).isAfter(assignment.getDueAt());
        if (late && !assignment.isAllowLateSubmission()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "마감된 과제이며 지각 제출이 허용되지 않습니다.");
        }

        Submission submission = submissionRepository.save(
                Submission.create(assignment, student, hasText ? textContent : null, late));

        List<StoredFile> uploaded = new ArrayList<>();
        try {
            String directory = "submissions/" + submission.getId();
            for (MultipartFile file : submittedFiles) {
                uploaded.add(fileStorage.upload(file, directory));
            }
            List<Attachment> attachments = uploaded.stream()
                    .map(stored -> Attachment.create(stored.originalName(), stored.storedName(),
                            stored.storageKey(), stored.extension(), stored.sizeKb()))
                    .toList();
            attachmentRepository.saveAll(attachments);
            attachmentRepository.flush();
            submissionAttachmentRepository.saveAll(attachments.stream()
                    .map(attachment -> SubmissionAttachment.create(submission, attachment))
                    .toList());
            submissionAttachmentRepository.flush();
            return SubmissionDetailResponse.from(submission, attachments);
        } catch (RuntimeException e) {
            compensate(uploaded);
            throw e;
        }
    }

    private void compensate(List<StoredFile> uploaded) {
        for (StoredFile file : uploaded) {
            try {
                fileStorage.delete(file.storageKey());
            } catch (RuntimeException e) {
                log.error("Failed to delete orphaned S3 object after submission save failure: storageKey={}",
                        file.storageKey(), e);
            }
        }
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
            if (!ALLOWED_EXTENSIONS.contains(extractExtension(file.getOriginalFilename()))) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "허용되지 않는 파일 확장자입니다.");
            }
            total += file.getSize();
        }
        if (total > MAX_TOTAL_SIZE_BYTES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "전체 파일 용량은 100MB를 초과할 수 없습니다.");
        }
    }

    private String extractExtension(String originalName) {
        if (originalName == null) return "";
        int lastDot = originalName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == originalName.length() - 1) return "";
        return originalName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    private Member requireStudent(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."));
        if (member.getRole() != MemberRole.STUDENT) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "학생만 이용할 수 있는 기능입니다.");
        }
        return member;
    }
}