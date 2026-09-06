package com.sat.lms.notice.controller;

import com.sat.lms.global.response.ApiResponse;
import com.sat.lms.global.response.PageResponse;
import com.sat.lms.notice.dto.NoticeCommentCreateRequest;
import com.sat.lms.notice.dto.NoticeCommentResponse;
import com.sat.lms.notice.dto.NoticeCommentUpdateRequest;
import com.sat.lms.notice.service.NoticeCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notice Comment API", description = "공지사항 댓글 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1")
public class NoticeCommentController {
    private final NoticeCommentService noticeCommentService;

    public NoticeCommentController(NoticeCommentService noticeCommentService) {
        this.noticeCommentService = noticeCommentService;
    }

    @Operation(summary = "공지사항 댓글 작성")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/notices/{noticeId}/comments")
    public ApiResponse<NoticeCommentResponse> create(@PathVariable Long noticeId,
                                                     @Valid @RequestBody NoticeCommentCreateRequest request,
                                                     @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("댓글을 작성했습니다.",
                noticeCommentService.create(noticeId, memberId, request.getContent()));
    }

    @Operation(summary = "공지사항 댓글 목록 조회")
    @GetMapping("/notices/{noticeId}/comments")
    public ApiResponse<PageResponse<NoticeCommentResponse>> getComments(
            @PathVariable Long noticeId,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("댓글 목록을 조회했습니다.",
                PageResponse.from(noticeCommentService.getComments(noticeId, memberId, pageable)));
    }

    @Operation(summary = "공지사항 댓글 수정")
    @PatchMapping("/notice-comments/{commentId}")
    public ApiResponse<NoticeCommentResponse> update(@PathVariable Long commentId,
                                                     @Valid @RequestBody NoticeCommentUpdateRequest request,
                                                     @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("댓글을 수정했습니다.",
                noticeCommentService.update(commentId, memberId, request.getContent()));
    }

    @Operation(summary = "공지사항 댓글 삭제")
    @DeleteMapping("/notice-comments/{commentId}")
    public ApiResponse<Void> delete(@PathVariable Long commentId, @AuthenticationPrincipal Long memberId) {
        noticeCommentService.delete(commentId, memberId);
        return ApiResponse.success("댓글을 삭제했습니다.", null);
    }
}
