package com.sat.lms.notice.dto;

public class UnreadCountResponse {
    private final long unreadCount;

    public UnreadCountResponse(long unreadCount) { this.unreadCount = unreadCount; }
    public long getUnreadCount() { return unreadCount; }
}
