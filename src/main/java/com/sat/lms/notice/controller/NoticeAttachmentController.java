package com.sat.lms.notice.controller;

import com.sat.lms.global.response.ApiResponse;
import com.sat.lms.notice.dto.NoticeAttachmentDownloadUrlResponse;
import com.sat.lms.notice.dto.NoticeAttachmentResponse;
import com.sat.lms.notice.service.NoticeAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Notice Attachment API", description = "공지 첨부파일 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
public class NoticeAttachmentController {
    private final NoticeAttachmentService noticeAttachmentService;

    public NoticeAttachmentController(NoticeAttachmentService noticeAttachmentService) {
        this.noticeAttachmentService = noticeAttachmentService;
    }

    @Operation(summary = "공지 첨부파일 추가")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(value = "/notices/{noticeId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<NoticeAttachmentResponse>> upload(
            @PathVariable Long noticeId,
            @Parameter(description = "첨부파일 목록", required = true,
                    array = @ArraySchema(schema = @Schema(type = "string", format = "binary")))
            @RequestPart(name = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("공지 첨부파일을 추가했습니다.",
                noticeAttachmentService.upload(noticeId, files, memberId));
    }

    @Operation(summary = "공지 첨부파일 다운로드 URL 발급")
    @GetMapping("/notice-attachments/{attachmentId}/download-url")
    public ApiResponse<NoticeAttachmentDownloadUrlResponse> getDownloadUrl(
            @PathVariable Long attachmentId, @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("다운로드 URL을 발급했습니다.",
                noticeAttachmentService.getDownloadUrl(attachmentId, memberId));
    }

    @Operation(summary = "공지 첨부파일 삭제")
    @DeleteMapping("/notice-attachments/{attachmentId}")
    public ApiResponse<Void> delete(@PathVariable Long attachmentId, @AuthenticationPrincipal Long memberId) {
        noticeAttachmentService.delete(attachmentId, memberId);
        return ApiResponse.success("공지 첨부파일을 삭제했습니다.", null);
    }
}
