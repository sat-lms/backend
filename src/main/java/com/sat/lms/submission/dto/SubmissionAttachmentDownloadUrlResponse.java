package com.sat.lms.submission.dto;

public class SubmissionAttachmentDownloadUrlResponse {
    private final String downloadUrl;
    private final long expiresIn;
    private final String originalName;

    public SubmissionAttachmentDownloadUrlResponse(String downloadUrl, long expiresIn, String originalName) {
        this.downloadUrl = downloadUrl;
        this.expiresIn = expiresIn;
        this.originalName = originalName;
    }

    public String getDownloadUrl() { return downloadUrl; }
    public long getExpiresIn() { return expiresIn; }
    public String getOriginalName() { return originalName; }
}