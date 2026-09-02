package com.sat.lms.notice.service;

import com.sat.lms.attachment.entity.Attachment;
import com.sat.lms.attachment.entity.NoticeAttachment;
import com.sat.lms.attachment.repository.AttachmentRepository;
import com.sat.lms.attachment.repository.NoticeAttachmentRepository;
import com.sat.lms.attachment.service.AttachmentFileValidator;
import com.sat.lms.attachment.service.AttachmentStorageLifecycle;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.storage.DownloadUrl;
import com.sat.lms.global.storage.FileStorage;
import com.sat.lms.global.storage.StoredFile;
import com.sat.lms.member.service.MemberGuard;
import com.sat.lms.notice.dto.NoticeAttachmentDownloadUrlResponse;
import com.sat.lms.notice.dto.NoticeAttachmentResponse;
import com.sat.lms.notice.entity.Notice;
import com.sat.lms.notice.repository.NoticeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class NoticeAttachmentService {
    private static final String NOT_FOUND_ATTACHMENT_MESSAGE = "존재하지 않는 공지 첨부파일입니다.";

    private final NoticeRepository noticeRepository;
    private final NoticeAttachmentRepository noticeAttachmentRepository;
    private final AttachmentRepository attachmentRepository;
    private final MemberGuard memberGuard;
    private final FileStorage fileStorage;
    private final NoticeAttachmentCleanup cleanup;
    private final AttachmentFileValidator fileValidator;
    private final AttachmentStorageLifecycle storageLifecycle;

    public NoticeAttachmentService(NoticeRepository noticeRepository,
                                   NoticeAttachmentRepository noticeAttachmentRepository,
                                   AttachmentRepository attachmentRepository,
                                   MemberGuard memberGuard,
                                   FileStorage fileStorage,
                                   NoticeAttachmentCleanup cleanup,
                                   AttachmentFileValidator fileValidator,
                                   AttachmentStorageLifecycle storageLifecycle) {
        this.noticeRepository = noticeRepository;
        this.noticeAttachmentRepository = noticeAttachmentRepository;
        this.attachmentRepository = attachmentRepository;
        this.memberGuard = memberGuard;
        this.fileStorage = fileStorage;
        this.cleanup = cleanup;
        this.fileValidator = fileValidator;
        this.storageLifecycle = storageLifecycle;
    }

    @Transactional
    public List<NoticeAttachmentResponse> upload(Long noticeId, List<MultipartFile> files, Long memberId) {
        memberGuard.requireAdmin(memberId);
        fileValidator.validateList(files);
        Notice notice = noticeRepository.findByIdForUpdate(noticeId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 공지사항입니다."));
        long existingCount = noticeAttachmentRepository.countByNoticeId(noticeId);
        if (existingCount + files.size() > AttachmentFileValidator.MAX_FILE_COUNT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "공지 첨부파일은 최대 3개까지 등록할 수 있습니다.");
        }
        fileValidator.validateContents(files);

        List<StoredFile> uploaded = uploadAll(files, "notices/" + noticeId);
        boolean rollbackCompensationRegistered = storageLifecycle.registerRollbackCompensation(uploaded);
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
            if (!rollbackCompensationRegistered) storageLifecycle.compensate(uploaded);
            throw exception;
        }
    }

    public NoticeAttachmentDownloadUrlResponse getDownloadUrl(Long attachmentId, Long memberId) {
        memberGuard.requireMember(memberId);
        NoticeAttachment link = findNoticeAttachment(attachmentId);
        Attachment attachment = link.getAttachment();
        DownloadUrl downloadUrl = fileStorage.createDownloadUrl(attachment.getStorageKey());
        return new NoticeAttachmentDownloadUrlResponse(
                downloadUrl.url(), downloadUrl.expiresInSeconds(), attachment.getOriginalName());
    }

    @Transactional
    public void delete(Long attachmentId, Long memberId) {
        memberGuard.requireAdmin(memberId);
        cleanup.deleteOne(attachmentId);
    }

    private List<StoredFile> uploadAll(List<MultipartFile> files, String directory) {
        List<StoredFile> uploaded = new ArrayList<>();
        try {
            for (MultipartFile file : files) uploaded.add(fileStorage.upload(file, directory));
            return uploaded;
        } catch (RuntimeException exception) {
            storageLifecycle.compensate(uploaded);
            throw exception;
        }
    }

    private NoticeAttachment findNoticeAttachment(Long attachmentId) {
        return noticeAttachmentRepository.findWithNoticeAndAttachmentByAttachmentId(attachmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_ATTACHMENT_MESSAGE));
    }

}
