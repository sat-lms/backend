package com.sat.lms.notice.controller;

import com.sat.lms.global.response.ApiResponse;
import com.sat.lms.global.response.PageResponse;
import com.sat.lms.notice.dto.NoticeCreateRequest;
import com.sat.lms.notice.dto.NoticeDetailResponse;
import com.sat.lms.notice.dto.NoticeListResponse;
import com.sat.lms.notice.dto.NoticeUpdateRequest;
import com.sat.lms.notice.dto.UnreadCountResponse;
import com.sat.lms.notice.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notice API", description = "공지사항 API")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/api/v1/notices")
public class NoticeController {
    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) { this.noticeService = noticeService; }

    @Operation(summary = "공지 목록 조회")
    @GetMapping
    public ApiResponse<PageResponse<NoticeListResponse>> getNotices(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("공지사항 목록을 조회했습니다.",
                PageResponse.from(noticeService.getNotices(memberId, unreadOnly, PageRequest.of(page, size))));
    }

    @Operation(summary = "미읽음 공지 개수 조회")
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> getUnreadCount(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("미읽음 공지 개수를 조회했습니다.", noticeService.getUnreadCount(memberId));
    }

    @Operation(summary = "공지 상세 조회", description = "상세 조회가 성공하면 현재 사용자의 읽음 기록이 생성됩니다.")
    @GetMapping("/{noticeId}")
    public ApiResponse<NoticeDetailResponse> getNotice(@PathVariable Long noticeId,
                                                       @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("공지사항을 조회했습니다.", noticeService.getNotice(noticeId, memberId));
    }

    @Operation(summary = "공지 등록")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<NoticeDetailResponse> create(@Valid @RequestBody NoticeCreateRequest request,
                                                    @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("공지사항을 등록했습니다.", noticeService.create(request, memberId));
    }

    @Operation(summary = "공지 수정")
    @PatchMapping("/{noticeId}")
    public ApiResponse<NoticeDetailResponse> update(@PathVariable Long noticeId,
                                                    @RequestBody NoticeUpdateRequest request,
                                                    @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("공지사항을 수정했습니다.", noticeService.update(noticeId, request, memberId));
    }

    @Operation(summary = "공지 삭제")
    @DeleteMapping("/{noticeId}")
    public ApiResponse<Void> delete(@PathVariable Long noticeId, @AuthenticationPrincipal Long memberId) {
        noticeService.delete(noticeId, memberId);
        return ApiResponse.success("공지사항을 삭제했습니다.", null);
    }
}
