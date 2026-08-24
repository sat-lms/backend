package com.sat.lms.submission.dto;

import com.sat.lms.attachment.entity.Attachment;

public class SubmissionFileResponse {
    private final Long attachmentId;
    private final String originalName;
    private final String extension;
    private final Long sizeKb;

    private SubmissionFileResponse(Attachment attachment) {
        this.attachmentId = attachment.getId();
        this.originalName = attachment.getOriginalName();
        this.extension = attachment.getExtension();
        this.sizeKb = attachment.getSizeKb();
    }

    public static SubmissionFileResponse from(Attachment attachment) {
        return new SubmissionFileResponse(attachment);
    }

    public Long getAttachmentId() { return attachmentId; }
    public String getOriginalName() { return originalName; }
    public String getExtension() { return extension; }
    public Long getSizeKb() { return sizeKb; }
}