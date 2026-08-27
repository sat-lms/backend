package com.sat.lms.notice.service;

import com.sat.lms.attachment.entity.Attachment;
import com.sat.lms.attachment.entity.NoticeAttachment;
import com.sat.lms.attachment.repository.AttachmentRepository;
import com.sat.lms.attachment.repository.NoticeAttachmentRepository;
import com.sat.lms.global.config.AwsProperties;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.storage.FileStorage;
import com.sat.lms.global.storage.StoredFile;
import com.sat.lms.member.entity.Member;
import com.sat.lms.member.entity.MemberRole;
import com.sat.lms.member.repository.MemberRepository;
import com.sat.lms.notice.dto.NoticeAttachmentDownloadUrlResponse;
import com.sat.lms.notice.dto.NoticeAttachmentResponse;
import com.sat.lms.notice.entity.Notice;
import com.sat.lms.notice.repository.NoticeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class NoticeAttachmentService {
    private static final Logger log = LoggerFactory.getLogger(NoticeAttachmentService.class);
    private static final int MAX_FILE_COUNT = 3;
    private static final int MAX_DELETE_ATTEMPTS = 3;
    private static final long DELETE_RETRY_DELAY_MILLIS = 100;
    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;
    private static final long MAX_TOTAL_SIZE_BYTES = 50L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "hwp", "hwpx", "doc", "docx",
            "ppt", "pptx", "xls", "xlsx", "zip");
    private static final String NOT_FOUND_ATTACHMENT_MESSAGE = "존재하지 않는 공지 첨부파일입니다.";

    private final NoticeRepository noticeRepository;
    private final NoticeAttachmentRepository noticeAttachmentRepository;
    private final AttachmentRepository attachmentRepository;
    private final MemberRepository memberRepository;
    private final FileStorage fileStorage;
    private final AwsProperties awsProperties;

    public NoticeAttachmentService(NoticeRepository noticeRepository,
                                   NoticeAttachmentRepository noticeAttachmentRepository,
                                   AttachmentRepository attachmentRepository,
                                   MemberRepository memberRepository,
                                   FileStorage fileStorage,
                                   AwsProperties awsProperties) {
        this.noticeRepository = noticeRepository;
        this.noticeAttachmentRepository = noticeAttachmentRepository;
        this.attachmentRepository = attachmentRepository;
        this.memberRepository = memberRepository;
        this.fileStorage = fileStorage;
        this.awsProperties = awsProperties;
    }

    @Transactional
    public List<NoticeAttachmentResponse> upload(Long noticeId, List<MultipartFile> files, Long memberId) {
        requireAdmin(memberId);
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 공지사항입니다."));
        validateFiles(files);

        List<StoredFile> uploaded = uploadAll(files, "notices/" + noticeId);
        boolean rollbackCompensationRegistered = registerRollbackCompensation(uploaded);
        try {
            List<Attachment> attachments = new ArrayList<>();
            for (StoredFile stored : uploaded) {
                attachments.add(attachmentRepository.save(Attachment.create(
                        stored.originalName(), stored.storedName(), stored.storageKey(),
                        stored.extension(), stored.sizeKb())));
            }
            attachmentRepository.flush();
            for (Attachment attachment : attachments) {
                noticeAttachmentRepository.save(NoticeAttachment.create(notice, attachment));
            }
            noticeAttachmentRepository.flush();
            return attachments.stream().map(NoticeAttachmentResponse::from).toList();
        } catch (RuntimeException exception) {
            if (!rollbackCompensationRegistered) compensate(uploaded);
            throw exception;
        }
    }

    public NoticeAttachmentDownloadUrlResponse getDownloadUrl(Long attachmentId, Long memberId) {
        requireMember(memberId);
        NoticeAttachment link = findNoticeAttachment(attachmentId);
        Attachment attachment = link.getAttachment();
        String downloadUrl = fileStorage.createDownloadUrl(attachment.getStorageKey());
        long expiresIn = Math.multiplyExact(awsProperties.getS3().getPresignedExpirationMinutes(), 60L);
        return new NoticeAttachmentDownloadUrlResponse(downloadUrl, expiresIn, attachment.getOriginalName());
    }

    @Transactional
    public void delete(Long attachmentId, Long memberId) {
        requireAdmin(memberId);
        NoticeAttachment link = findNoticeAttachment(attachmentId);
        Attachment attachment = attachmentRepository.findByIdForUpdate(attachmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_ATTACHMENT_MESSAGE));
        boolean shared = noticeAttachmentRepository.countByAttachmentId(attachmentId) > 1
                || attachmentRepository.existsAssignmentLink(attachmentId)
                || attachmentRepository.existsSubmissionLink(attachmentId);

        noticeAttachmentRepository.delete(link);
        noticeAttachmentRepository.flush();
        if (!shared) {
            attachmentRepository.delete(attachment);
            attachmentRepository.flush();
            deleteAfterCommit(attachment.getStorageKey());
        }
    }

    private List<StoredFile> uploadAll(List<MultipartFile> files, String directory) {
        List<StoredFile> uploaded = new ArrayList<>();
        try {
            for (MultipartFile file : files) uploaded.add(fileStorage.upload(file, directory));
            return uploaded;
        } catch (RuntimeException exception) {
            compensate(uploaded);
            throw exception;
        }
    }

    private boolean registerRollbackCompensation(List<StoredFile> uploaded) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return false;
        List<StoredFile> requestFiles = List.copyOf(uploaded);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) compensate(requestFiles);
            }
        });
        return true;
    }

    private void deleteAfterCommit(String storageKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteWithRetry(storageKey);
                }
            });
        } else {
            deleteWithRetry(storageKey);
        }
    }

    private void compensate(List<StoredFile> uploaded) {
        for (StoredFile stored : uploaded) deleteWithRetry(stored.storageKey());
    }

    private void deleteWithRetry(String storageKey) {
        for (int attempt = 1; attempt <= MAX_DELETE_ATTEMPTS; attempt++) {
            try {
                fileStorage.delete(storageKey);
                return;
            } catch (RuntimeException exception) {
                if (attempt == MAX_DELETE_ATTEMPTS) {
                    log.error("S3 object cleanup failed after {} attempts", MAX_DELETE_ATTEMPTS);
                    return;
                }
                log.warn("Retrying S3 object cleanup (attempt {}/{})", attempt, MAX_DELETE_ATTEMPTS);
                if (!sleepBeforeRetry()) return;
            }
        }
    }

    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(DELETE_RETRY_DELAY_MILLIS);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting to retry S3 object cleanup");
            return false;
        }
    }

    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "첨부할 파일을 입력해주세요.");
        }
        if (files.size() > MAX_FILE_COUNT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "파일은 최대 3개까지 첨부할 수 있습니다.");
        }
        long totalSize = 0;
        for (MultipartFile file : files) {
            validateFile(file);
            try {
                totalSize = Math.addExact(totalSize, file.getSize());
            } catch (ArithmeticException exception) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "전체 파일 용량은 50MB를 초과할 수 없습니다.");
            }
        }
        if (totalSize > MAX_TOTAL_SIZE_BYTES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "전체 파일 용량은 50MB를 초과할 수 없습니다.");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "빈 파일은 업로드할 수 없습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "파일 1개의 용량은 20MB를 초과할 수 없습니다.");
        }
        String originalName = file.getOriginalFilename();
        if (!isSafeOriginalName(originalName)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "올바르지 않은 파일명입니다.");
        }
        if (!ALLOWED_EXTENSIONS.contains(extractExtension(originalName))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "허용되지 않는 파일 확장자입니다.");
        }
    }

    private boolean isSafeOriginalName(String name) {
        return name != null && !name.isBlank() && name.equals(name.trim()) && name.length() <= 255
                && !name.equals(".") && !name.equals("..") && !name.contains("..")
                && !name.contains("/") && !name.contains("\\")
                && name.chars().noneMatch(Character::isISOControl);
    }

    private String extractExtension(String originalName) {
        int lastDot = originalName.lastIndexOf('.');
        if (lastDot < 1 || lastDot == originalName.length() - 1) return "";
        return originalName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    private NoticeAttachment findNoticeAttachment(Long attachmentId) {
        return noticeAttachmentRepository.findWithNoticeAndAttachmentByAttachmentId(attachmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_ATTACHMENT_MESSAGE));
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
}
