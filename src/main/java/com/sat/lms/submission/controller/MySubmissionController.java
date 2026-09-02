package com.sat.lms.submission.controller;

import com.sat.lms.global.response.ApiResponse;
import com.sat.lms.global.response.PageResponse;
import com.sat.lms.submission.dto.SubmissionListResponse;
import com.sat.lms.submission.dto.MySubmissionSort;
import com.sat.lms.global.exception.BusinessException;
import com.sat.lms.submission.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;

@Tag(name = "My Submission API", description = "내 제출 내역 API")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/api/v1/members/me/submissions")
public class MySubmissionController {
    private final SubmissionService submissionService;

    public MySubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @Operation(summary = "내 제출 내역 목록 조회",
            description = "과제 기준으로 현재 학생의 제출을 연결합니다. 미제출 포함 기본값은 true이며 "
                    + "dueAtDesc(기본), dueAtAsc, submittedAtDesc 정렬만 허용합니다.")
    @Parameters({
            @Parameter(name = "page", description = "페이지 번호(0부터 시작)", example = "0"),
            @Parameter(name = "size", description = "페이지 크기", example = "20"),
            @Parameter(name = "includeNotSubmitted", description = "미제출 과제 포함 여부(기본 true)", example = "true"),
            @Parameter(name = "sort", description = "dueAtDesc | dueAtAsc | submittedAtDesc", example = "dueAtDesc")
    })
    @GetMapping
    public ApiResponse<PageResponse<SubmissionListResponse>> getMySubmissions(
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable,
            @Parameter(hidden = true) HttpServletRequest request,
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("제출 내역을 조회했습니다.",
                PageResponse.from(submissionService.getMySubmissions(memberId,
                        parseIncludeNotSubmitted(request.getParameterValues("includeNotSubmitted")),
                        parseSort(request.getParameterValues("sort")), pageable)));
    }

    private boolean parseIncludeNotSubmitted(String[] values) {
        if (values == null) return true;
        if (values.length != 1 || !(values[0].equals("true") || values[0].equals("false"))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "includeNotSubmitted는 true 또는 false여야 합니다.");
        }
        return Boolean.parseBoolean(values[0]);
    }

    private MySubmissionSort parseSort(String[] values) {
        if (values == null) return MySubmissionSort.DUE_AT_DESC;
        if (values.length != 1) throw invalidSort();
        try {
            return MySubmissionSort.from(values[0]);
        } catch (IllegalArgumentException exception) {
            throw invalidSort();
        }
    }

    private BusinessException invalidSort() {
        return new BusinessException(HttpStatus.BAD_REQUEST,
                "sort는 dueAtDesc, dueAtAsc, submittedAtDesc 중 하나여야 합니다.");
    }
}
