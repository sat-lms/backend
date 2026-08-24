package com.sat.lms.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class NoticeCreateRequest {
    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    private Boolean isPinned;

    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Boolean getIsPinned() { return isPinned; }
}
