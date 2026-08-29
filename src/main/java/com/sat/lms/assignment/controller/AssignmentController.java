package com.sat.lms.assignment.controller;

import com.sat.lms.assignment.dto.AssignmentCreateRequest;
import com.sat.lms.assignment.dto.AssignmentDetailResponse;
import com.sat.lms.assignment.dto.AssignmentListResponse;
import com.sat.lms.assignment.dto.AssignmentUpdateRequest;
import com.sat.lms.assignment.service.AssignmentService;
import com.sat.lms.global.response.ApiResponse;
import com.sat.lms.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Assignment API", description = "과제 API")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/api/v1/assignments")
public class AssignmentController {
    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @Operation(summary = "과제 등록")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<AssignmentDetailResponse> create(@Valid @RequestBody AssignmentCreateRequest request,
                                                        @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("과제를 등록했습니다.", assignmentService.create(request, memberId));
    }

    @Operation(summary = "과제 목록 조회",
            description = "기본 정렬은 dueAt,asc이며 createdAt, updatedAt, dueAt, title 중 하나만 asc/desc로 정렬할 수 있습니다.")
    @GetMapping
    public ApiResponse<PageResponse<AssignmentListResponse>> getAssignments(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("과제 목록을 조회했습니다.",
                PageResponse.from(assignmentService.getAssignments(memberId, pageable)));
    }

    @Operation(summary = "과제 상세 조회")
    @GetMapping("/{assignmentId}")
    public ApiResponse<AssignmentDetailResponse> getAssignment(@PathVariable Long assignmentId,
                                                               @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("과제를 조회했습니다.",
                assignmentService.getAssignment(assignmentId, memberId));
    }

    @Operation(summary = "과제 수정")
    @PatchMapping("/{assignmentId}")
    public ApiResponse<AssignmentDetailResponse> update(@PathVariable Long assignmentId,
                                                        @RequestBody AssignmentUpdateRequest request,
                                                        @AuthenticationPrincipal Long memberId) {
        return ApiResponse.success("과제를 수정했습니다.",
                assignmentService.update(assignmentId, request, memberId));
    }

    @Operation(summary = "과제 삭제")
    @DeleteMapping("/{assignmentId}")
    public ApiResponse<Void> delete(@PathVariable Long assignmentId,
                                    @AuthenticationPrincipal Long memberId) {
        assignmentService.delete(assignmentId, memberId);
        return ApiResponse.success("과제를 삭제했습니다.", null);
    }
}
