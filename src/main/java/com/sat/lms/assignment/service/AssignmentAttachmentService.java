package com.sat.lms.assignment.service;

import com.sat.lms.assignment.dto.AssignmentAttachmentDownloadUrlResponse;
import com.sat.lms.assignment.dto.AssignmentAttachmentResponse;
import com.sat.lms.assignment.entity.Assignment;
import com.sat.lms.assignment.repository.AssignmentRepository;
import com.sat.lms.attachment.entity.AssignmentAttachment;
import com.sat.lms.attachment.entity.Attachment;
import com.sat.lms.attachment.repository.AssignmentAttachmentRepository;
import com.sat.lms.attachment.repository.AttachmentRepository;
import com.sat.lms.attachment.service.AttachmentFileValidator;
import com.sat.lms.attachment.service.AttachmentStorageLifecycle;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.global.storage.DownloadUrl;
import com.sat.lms.global.storage.FileStorage;
import com.sat.lms.global.storage.StoredFile;
import com.sat.lms.global.transaction.ShortTransactionExecutor;
import com.sat.lms.member.service.MemberGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
public class AssignmentAttachmentService {
    private static final String NOT_FOUND_MESSAGE = "존재하지 않는 과제 첨부파일입니다.";

    private final AssignmentRepository assignmentRepository;
    private final AssignmentAttachmentRepository assignmentAttachmentRepository;
    private final AttachmentRepository attachmentRepository;
    private final MemberGuard memberGuard;
    private final FileStorage fileStorage;
    private final AttachmentFileValidator fileValidator;
    private final AttachmentStorageLifecycle storageLifecycle;
    private final AssignmentAttachmentCleanup cleanup;
    private final ShortTransactionExecutor transactions;

    public AssignmentAttachmentService(AssignmentRepository assignmentRepository,
                                       AssignmentAttachmentRepository assignmentAttachmentRepository,
                                       AttachmentRepository attachmentRepository,
                                       MemberGuard memberGuard,
                                       FileStorage fileStorage,
                                       AttachmentFileValidator fileValidator,
                                       AttachmentStorageLifecycle storageLifecycle,
                                       AssignmentAttachmentCleanup cleanup,
                                       ShortTransactionExecutor transactions) {
        this.assignmentRepository = assignmentRepository;
        this.assignmentAttachmentRepository = assignmentAttachmentRepository;
        this.attachmentRepository = attachmentRepository;
        this.memberGuard = memberGuard;
        this.fileStorage = fileStorage;
        this.fileValidator = fileValidator;
        this.storageLifecycle = storageLifecycle;
        this.cleanup = cleanup;
        this.transactions = transactions;
    }

    public List<AssignmentAttachmentResponse> upload(Long assignmentId, List<MultipartFile> files, Long memberId) {
        transactions.requireNonTransactionalEntry();
        transactions.read(() -> { memberGuard.requireAdmin(memberId); return null; });
        fileValidator.validateList(files);
        fileValidator.validateContents(files);
        transactions.read(() -> { requireAssignment(assignmentId, files.size(), false); return null; });

        List<StoredFile> uploaded = uploadAll(files, "assignments/" + assignmentId);
        try {
            return transactions.write(() -> saveUploaded(assignmentId, memberId, uploaded));
        } catch (RuntimeException exception) {
            storageLifecycle.compensate(uploaded);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public AssignmentAttachmentDownloadUrlResponse getDownloadUrl(Long attachmentId, Long memberId) {
        memberGuard.requireMember(memberId);
        AssignmentAttachment link = findAssignmentAttachment(attachmentId);
        Attachment attachment = link.getAttachment();
        DownloadUrl downloadUrl = fileStorage.createDownloadUrl(attachment.getStorageKey());
        return new AssignmentAttachmentDownloadUrlResponse(
                downloadUrl.url(), downloadUrl.expiresInSeconds(), attachment.getOriginalName());
    }

    public void delete(Long attachmentId, Long memberId) {
        transactions.requireNonTransactionalEntry();
        List<String> keys = transactions.write(() -> { memberGuard.requireAdmin(memberId); return cleanup.deleteOne(attachmentId); });
        storageLifecycle.delete(keys);
    }

    private List<AssignmentAttachmentResponse> saveUploaded(Long assignmentId, Long memberId, List<StoredFile> uploaded) {
        memberGuard.requireAdmin(memberId);
        Assignment assignment = requireAssignment(assignmentId, uploaded.size(), true);
        List<Attachment> attachments = new ArrayList<>();
        for (StoredFile stored : uploaded) attachments.add(attachmentRepository.save(Attachment.create(
                stored.originalName(), stored.storedName(), stored.storageKey(), stored.extension(), stored.sizeKb())));
        attachmentRepository.flush();
        for (Attachment attachment : attachments) assignmentAttachmentRepository.save(AssignmentAttachment.create(assignment, attachment));
        assignmentAttachmentRepository.flush();
        return attachments.stream().map(AssignmentAttachmentResponse::from).toList();
    }

    private Assignment requireAssignment(Long id, int added, boolean lock) {
        Assignment assignment = (lock ? assignmentRepository.findByIdForUpdate(id) : assignmentRepository.findById(id))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 과제입니다."));
        if (assignmentAttachmentRepository.countByAssignmentId(id) + added > AttachmentFileValidator.MAX_FILE_COUNT)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "과제 첨부파일은 최대 3개까지 등록할 수 있습니다.");
        return assignment;
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

    private AssignmentAttachment findAssignmentAttachment(Long attachmentId) {
        return assignmentAttachmentRepository.findWithAssignmentAndAttachmentByAttachmentId(attachmentId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE));
    }

}
