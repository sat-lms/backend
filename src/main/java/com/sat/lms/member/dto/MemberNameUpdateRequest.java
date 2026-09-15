package com.sat.lms.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "내 이름 변경 요청")
public class MemberNameUpdateRequest {

    @Schema(description = "변경할 이름. 앞뒤 공백은 제거되며, 중간 공백은 허용됩니다.", example = "김 새이")
    @NotBlank(message = "이름은 필수이며 공백만 입력할 수 없습니다.")
    @Size(max = 20, message = "이름은 20자 이하여야 합니다.")
    private String name;

    protected MemberNameUpdateRequest() {
    }

    public MemberNameUpdateRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
