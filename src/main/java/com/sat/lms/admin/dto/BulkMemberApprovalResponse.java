package com.sat.lms.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record BulkMemberApprovalResponse(
        @Schema(description = "중복 제거 후 실제 대상 ID 수") int requestedCount,
        int approvedCount, int skippedCount, List<Result> results) {
    public enum Outcome { APPROVED, SKIPPED }
    public enum Reason { NOT_PENDING, NOT_FOUND }
    public record Result(Long memberId, Outcome result, Reason reason) {}
}
