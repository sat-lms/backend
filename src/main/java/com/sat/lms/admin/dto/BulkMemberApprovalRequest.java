package com.sat.lms.admin.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BulkMemberApprovalRequest(
        @Schema(description = "원본 배열 최대 100개. 중복 ID는 최초 순서대로 한 번만 처리합니다.", example = "[12,15,18]")
        @NotEmpty @Size(max = 100)
        @JsonDeserialize(contentUsing = StrictMemberIdDeserializer.class)
        List<@NotNull @Positive Long> memberIds) {
}
