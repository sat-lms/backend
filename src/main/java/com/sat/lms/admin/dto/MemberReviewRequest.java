package com.sat.lms.admin.dto;

import com.sat.lms.member.entity.MemberReviewAction;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "가입 신청 심사 요청")
public class MemberReviewRequest {

    @Schema(description = "심사 결과", example = "APPROVED")
    private MemberReviewAction action;

    @Schema(description = "거절 사유 (action 이 REJECTED 일 때 필수)", example = "학번 정보를 확인할 수 없습니다.")
    private String rejectionReason;

    public MemberReviewAction getAction() {
        return action;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }
}