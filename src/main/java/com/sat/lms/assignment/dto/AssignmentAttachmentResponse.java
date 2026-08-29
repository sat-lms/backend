package com.sat.lms.assignment.dto;

import com.sat.lms.attachment.entity.Attachment;

import java.util.Locale;

public class AssignmentAttachmentResponse {
    private final Long attachmentId;
    private final String originalName;
    private final String extension;
    private final Long sizeKb;
    private final String formattedSize;

    private AssignmentAttachmentResponse(Attachment attachment) {
        this.attachmentId = attachment.getId();
        this.originalName = attachment.getOriginalName();
        this.extension = attachment.getExtension();
        this.sizeKb = attachment.getSizeKb();
        this.formattedSize = formatSize(attachment.getSizeKb());
    }

    public static AssignmentAttachmentResponse from(Attachment attachment) {
        return new AssignmentAttachmentResponse(attachment);
    }

    private static String formatSize(long sizeKb) {
        if (sizeKb < 1024) return sizeKb + " KB";
        return String.format(Locale.ROOT, "%.1f MB", sizeKb / 1024.0);
    }

    public Long getAttachmentId() { return attachmentId; }
    public String getOriginalName() { return originalName; }
    public String getExtension() { return extension; }
    public Long getSizeKb() { return sizeKb; }
    public String getFormattedSize() { return formattedSize; }
}
