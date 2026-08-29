package com.sat.lms.assignment.dto;

import com.sat.lms.attachment.entity.Attachment;

public class AssignmentAttachmentDetailResponse {
    private final Long attachmentId;
    private final String originalName;
    private final String extension;
    private final Long sizeKb;

    private AssignmentAttachmentDetailResponse(Attachment attachment) {
        this.attachmentId = attachment.getId();
        this.originalName = attachment.getOriginalName();
        this.extension = attachment.getExtension();
        this.sizeKb = attachment.getSizeKb();
    }

    public static AssignmentAttachmentDetailResponse from(Attachment attachment) {
        return new AssignmentAttachmentDetailResponse(attachment);
    }

    public Long getAttachmentId() { return attachmentId; }
    public String getOriginalName() { return originalName; }
    public String getExtension() { return extension; }
    public Long getSizeKb() { return sizeKb; }
}
